package com.pcthub;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Account registration and management. Every endpoint here is ADMIN only. */
@RestController
@RequestMapping("/api/admin")
class AdminUserController {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");
    private static final Pattern STUDENT_NO = Pattern.compile("^[A-Za-z0-9-]{3,40}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+() \\-]{5,40}$");
    private static final Set<String> ALL_ROLES = Set.of("STUDENT", "TEACHER", "CASHIER", "ADMIN");
    private static final Set<String> REGISTERABLE_ROLES = Set.of("STUDENT", "TEACHER", "CASHIER");

    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    AdminUserController(JdbcTemplate db, PasswordEncoder encoder, PlatformTransactionManager txManager) {
        this.db = db;
        this.encoder = encoder;
        this.tx = new TransactionTemplate(txManager);
    }

    @GetMapping("/users")
    List<Map<String, Object>> users(@RequestParam(required = false) String role, Authentication auth) {
        Access.require(auth, "ADMIN");
        String sql = "SELECT u.id,u.username,u.full_name,u.role,COALESCE(u.student_number,'') student_number,u.active,"
            + "COALESCE(a.total_fees,0) total_fees,DATE_FORMAT(u.created_at,'%Y-%m-%d %H:%i') created_at,"
            + "COALESCE(p.course,'') course,COALESCE(p.year_level,'') year_level,COALESCE(p.pct_email,'') pct_email,"
            + "COALESCE(p.personal_email,'') personal_email,COALESCE(p.contact_no,'') contact_no "
            + "FROM users u LEFT JOIN student_accounts a ON a.user_id=u.id LEFT JOIN user_profiles p ON p.user_id=u.id";
        if (role != null && !role.isBlank()) {
            String r = role.trim().toUpperCase(Locale.ROOT);
            if (!ALL_ROLES.contains(r)) throw Access.bad("Unknown role");
            return db.queryForList(sql + " WHERE u.role=? ORDER BY u.full_name", r);
        }
        return db.queryForList(sql + " ORDER BY u.role,u.full_name");
    }

    @PostMapping("/users")
    Map<String, Object> register(@RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        final String fullName = Access.text(body, "fullName");
        final String username = Access.text(body, "username");
        final String role = Access.text(body, "role").toUpperCase(Locale.ROOT);
        final String studentNo = Access.text(body, "studentNumber");
        final String password = rawPassword(body.get("password"));
        final BigDecimal fees = fees(body.get("totalFees"));
        final String[] profile = profile(body);

        if (!REGISTERABLE_ROLES.contains(role)) throw Access.bad("Choose Student, Teacher or Cashier");
        validateName(fullName);
        if (!USERNAME.matcher(username).matches()) throw Access.bad("Username must be 3 to 40 letters, numbers, dots, dashes or underscores");
        validatePassword(password);
        final boolean student = role.equals("STUDENT");
        if (student && !STUDENT_NO.matcher(studentNo).matches()) throw Access.bad("A student number (letters, numbers, dashes) is required for students");

        if (count("SELECT COUNT(*) FROM users WHERE username=?", username) > 0) throw Access.conflict("That username is already taken");
        if (student && count("SELECT COUNT(*) FROM users WHERE student_number=?", studentNo) > 0) throw Access.conflict("That student number is already registered");

        try {
            Long id = tx.execute(status -> {
                db.update("INSERT INTO users(username,password_hash,full_name,role,student_number) VALUES (?,?,?,?,?)",
                    username, encoder.encode(password), fullName, role, student ? studentNo : null);
                Long newId = db.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
                if (student) db.update("INSERT INTO student_accounts(user_id,total_fees) VALUES (?,?)", newId, fees);
                saveProfile(newId, profile);
                return newId;
            });
            return Map.of("message", "Account created for " + fullName, "id", id == null ? 0L : id);
        } catch (DuplicateKeyException e) {
            throw Access.conflict("That username or student number is already registered");
        }
    }

    @PutMapping("/users/{id}")
    Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        List<Map<String, Object>> rows = db.queryForList("SELECT id,role,active FROM users WHERE id=?", id);
        if (rows.isEmpty()) throw Access.notFound("Account not found");
        final String role = String.valueOf(rows.get(0).get("role"));
        final boolean wasActive = Boolean.TRUE.equals(rows.get(0).get("active"));

        final String fullName = Access.text(body, "fullName");
        final String studentNo = Access.text(body, "studentNumber");
        final String newPassword = rawPassword(body.get("newPassword"));
        final boolean active = body.containsKey("active") ? Access.flag(body.get("active")) : wasActive;
        final boolean student = role.equals("STUDENT");
        final BigDecimal fees = student && !Access.text(body, "totalFees").isEmpty() ? fees(body.get("totalFees")) : null;

        final String[] profile = profile(body);

        validateName(fullName);
        if (student && !STUDENT_NO.matcher(studentNo).matches()) throw Access.bad("A valid student number is required for students");
        if (!newPassword.isEmpty()) validatePassword(newPassword);
        if (!active && id == Access.userId(auth)) throw Access.bad("You cannot deactivate your own account");
        if (!active && wasActive && role.equals("ADMIN") && activeAdmins() <= 1) throw Access.bad("At least one active administrator is required");

        try {
            tx.executeWithoutResult(status -> {
                db.update("UPDATE users SET full_name=?,active=? WHERE id=?", fullName, active, id);
                if (student) db.update("UPDATE users SET student_number=? WHERE id=?", studentNo, id);
                if (!newPassword.isEmpty()) db.update("UPDATE users SET password_hash=? WHERE id=?", encoder.encode(newPassword), id);
                if (fees != null) db.update("INSERT INTO student_accounts(user_id,total_fees) VALUES (?,?) ON DUPLICATE KEY UPDATE total_fees=VALUES(total_fees)", id, fees);
                saveProfile(id, profile);
            });
        } catch (DuplicateKeyException e) {
            throw Access.conflict("That student number is already registered to another student");
        }
        return Map.of("message", "Account updated");
    }

    @DeleteMapping("/users/{id}")
    Map<String, Object> delete(@PathVariable long id, Authentication auth) {
        Access.require(auth, "ADMIN");
        if (id == Access.userId(auth)) throw Access.bad("You cannot delete your own account");
        List<Map<String, Object>> rows = db.queryForList("SELECT role,active FROM users WHERE id=?", id);
        if (rows.isEmpty()) throw Access.notFound("Account not found");
        boolean isActiveAdmin = "ADMIN".equals(String.valueOf(rows.get(0).get("role"))) && Boolean.TRUE.equals(rows.get(0).get("active"));
        if (isActiveAdmin && activeAdmins() <= 1) throw Access.bad("At least one active administrator is required");
        try {
            db.update("DELETE FROM users WHERE id=?", id);
        } catch (DataIntegrityViolationException e) {
            throw Access.conflict("This account has payments, tasks or posts linked to it, so it cannot be deleted. Deactivate it instead.");
        }
        return Map.of("message", "Account deleted");
    }

    // ------------------------------------------------------------------ helpers

    private int count(String sql, Object arg) {
        Integer n = db.queryForObject(sql, Integer.class, arg);
        return n == null ? 0 : n;
    }

    private int activeAdmins() {
        Integer n = db.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND active=TRUE", Integer.class);
        return n == null ? 0 : n;
    }

    /** {course, yearLevel, pctEmail, personalEmail, contactNo}; blank values become null. */
    private static String[] profile(Map<String, Object> body) {
        String course = Access.text(body, "course");
        String year = Access.text(body, "yearLevel");
        String pct = Access.text(body, "pctEmail");
        String personal = Access.text(body, "personalEmail");
        String contact = Access.text(body, "contactNo");
        if (course.length() > 160) throw Access.bad("Course must be 160 characters or fewer");
        if (year.length() > 20) throw Access.bad("Year level must be 20 characters or fewer");
        if (!pct.isEmpty() && (pct.length() > 160 || !EMAIL.matcher(pct).matches())) throw Access.bad("Enter a valid PCT email address");
        if (!personal.isEmpty() && (personal.length() > 160 || !EMAIL.matcher(personal).matches())) throw Access.bad("Enter a valid personal email address");
        if (!contact.isEmpty() && !PHONE.matcher(contact).matches()) throw Access.bad("Contact number may only contain digits, spaces, + ( ) and -");
        return new String[]{blankToNull(course), blankToNull(year), blankToNull(pct), blankToNull(personal), blankToNull(contact)};
    }

    private void saveProfile(Long userId, String[] p) {
        db.update("INSERT INTO user_profiles(user_id,course,year_level,pct_email,personal_email,contact_no) VALUES (?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE course=VALUES(course),year_level=VALUES(year_level),pct_email=VALUES(pct_email),"
            + "personal_email=VALUES(personal_email),contact_no=VALUES(contact_no)", userId, p[0], p[1], p[2], p[3], p[4]);
    }

    private static String blankToNull(String s) { return s.isEmpty() ? null : s; }

    private static String rawPassword(Object v) { return v == null ? "" : String.valueOf(v); }

    private static void validateName(String fullName) {
        if (fullName.length() < 2 || fullName.length() > 160) throw Access.bad("Full name must be 2 to 160 characters");
    }

    private static void validatePassword(String password) {
        if (password.length() < 8 || password.length() > 72) throw Access.bad("Password must be 8 to 72 characters");
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) throw Access.bad("Password must contain at least one letter and one number");
    }

    private static BigDecimal fees(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try {
            BigDecimal d = new BigDecimal(s);
            if (d.signum() < 0 || d.scale() > 2 || d.compareTo(new BigDecimal("99999999.99")) > 0) throw new NumberFormatException();
            return d;
        } catch (NumberFormatException e) {
            throw Access.bad("Total fees must be a positive amount with at most two decimals");
        }
    }
}
