package com.pcthub;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Class cards. A class card exists per student, per subject, per term.
 *  - ADMIN creates / edits / deletes class cards.
 *  - CASHIER (or ADMIN) records the payment for ONE specific class card.
 *  - The printable class card is only released after that payment exists.
 *  - The assigned TEACHER submits the final grade once; after that only ADMIN can change it.
 * Class card payments are kept in their own table so they never change a student's tuition balance.
 */
@RestController
@RequestMapping("/api")
class ClassCardController {
    private static final DateTimeFormatter SQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> METHODS = Set.of("CASH", "BANK_TRANSFER", "GCASH", "OTHER");
    private static final Pattern SCHOOL_YEAR = Pattern.compile("^\\d{4}-\\d{4}$");
    private static final Pattern SUBJECT_CODE = Pattern.compile("^[A-Za-z0-9 .*\\-]{2,30}$");
    private static final Pattern GRADE = Pattern.compile("^[A-Za-z0-9.+\\-]{1,10}$");
    private static final Pattern OR_NUMBER = Pattern.compile("^[A-Za-z0-9\\-]{1,30}$");
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9\\-]{6,20}$");
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String CORE =
        "c.id,c.school_year,c.semester,c.subject_code,c.subject_description,c.units,c.mode_of_delivery,"
        + "DATE_FORMAT(c.start_date,'%Y-%m-%d') start_date,DATE_FORMAT(c.end_date,'%Y-%m-%d') end_date,c.schedule,c.fee,"
        + "c.student_id,s.full_name student_name,s.student_number,c.teacher_id,t.full_name teacher_name,"
        + "c.final_grade,c.remarks,DATE_FORMAT(c.finalized_at,'%Y-%m-%d %H:%i:%s') finalized_at,"
        + "(p.id IS NOT NULL) paid,p.reference_no payment_reference,p.or_number,DATE_FORMAT(p.paid_at,'%Y-%m-%d %H:%i:%s') paid_at";
    private static final String JOINS =
        " FROM class_cards c JOIN users s ON s.id=c.student_id JOIN users t ON t.id=c.teacher_id"
        + " LEFT JOIN class_card_payments p ON p.class_card_id=c.id";
    private static final String LIST_SQL = "SELECT " + CORE + JOINS;
    private static final String FULL_SQL = "SELECT " + CORE
        + ",c.verify_code,sp.course,sp.year_level,sp.pct_email student_pct_email,sp.personal_email student_personal_email,sp.contact_no student_contact,"
        + "tp.pct_email teacher_email,tp.contact_no teacher_contact,cr.full_name prepared_by,fz.full_name finalized_by_name"
        + JOINS
        + " LEFT JOIN user_profiles sp ON sp.user_id=c.student_id LEFT JOIN user_profiles tp ON tp.user_id=c.teacher_id"
        + " JOIN users cr ON cr.id=c.created_by LEFT JOIN users fz ON fz.id=c.finalized_by";

    private final JdbcTemplate db;
    private final TransactionTemplate tx;

    ClassCardController(JdbcTemplate db, PlatformTransactionManager txManager) {
        this.db = db;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ read

    @GetMapping("/class-cards")
    List<Map<String, Object>> list(Authentication auth) {
        String role = Access.role(auth);
        long me = Access.userId(auth);
        String order = " ORDER BY c.school_year DESC,c.semester,c.subject_code,s.full_name LIMIT 1000";
        List<Map<String, Object>> rows = switch (role) {
            case "STUDENT" -> db.queryForList(LIST_SQL + " WHERE c.student_id=?" + order, me);
            case "TEACHER" -> db.queryForList(LIST_SQL + " WHERE c.teacher_id=?" + order, me);
            case "CASHIER", "ADMIN" -> db.queryForList(LIST_SQL + order);
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role is not allowed to perform this action");
        };
        for (Map<String, Object> r : rows) {
            boolean paid = isPaid(r);
            // a student sees the grade only after the class card is paid; the cashier never sees grades
            if (role.equals("CASHIER") || (role.equals("STUDENT") && !paid)) {
                r.put("final_grade", null);
                r.put("remarks", null);
            }
        }
        return rows;
    }

    /** The full class card for printing. Only released once the card is paid. */
    @GetMapping("/class-cards/{id}")
    Map<String, Object> card(@PathVariable long id, Authentication auth) {
        String role = Access.role(auth);
        long me = Access.userId(auth);
        List<Map<String, Object>> rows = db.queryForList(FULL_SQL + " WHERE c.id=?", id);
        if (rows.isEmpty()) throw Access.notFound("Class card not found");
        Map<String, Object> c = rows.get(0);
        switch (role) {
            case "ADMIN" -> { }
            case "STUDENT" -> { if (((Number) c.get("student_id")).longValue() != me) throw Access.notFound("Class card not found"); }
            case "TEACHER" -> { if (((Number) c.get("teacher_id")).longValue() != me) throw Access.notFound("Class card not found"); }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your role is not allowed to view class cards");
        }
        if (!isPaid(c)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                "Payment required: pay " + c.get("fee") + " PHP for this class card at the PCT Cashier to unlock it.");
        }
        return c;
    }

    /** Any signed-in user (for example a proctor) can check a card by its verification code. */
    @GetMapping("/class-cards/verify/{code}")
    Map<String, Object> verify(@PathVariable String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) throw Access.bad("That is not a valid verification code");
        List<Map<String, Object>> rows = db.queryForList(LIST_SQL + " WHERE c.verify_code=?", normalized);
        if (rows.isEmpty()) throw Access.notFound("No class card matches that code");
        Map<String, Object> c = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", true);
        out.put("paid", isPaid(c));
        out.put("studentName", c.get("student_name"));
        out.put("studentNumber", c.get("student_number"));
        out.put("subjectCode", c.get("subject_code"));
        out.put("subjectDescription", c.get("subject_description"));
        out.put("schoolYear", c.get("school_year"));
        out.put("semester", c.get("semester"));
        out.put("teacherName", c.get("teacher_name"));
        out.put("paidAt", c.get("paid_at"));
        out.put("graded", c.get("final_grade") != null);
        return out;
    }

    // ------------------------------------------------------------------ admin: create / edit / delete

    @PostMapping("/class-cards")
    Map<String, Object> create(@RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        Fields f = Fields.parse(body, true);
        List<Long> studentIds = studentIds(body);
        requireTeacher(f.teacherId());
        long me = Access.userId(auth);
        int created = 0;
        int skipped = 0;
        for (long studentId : studentIds) {
            if (count("SELECT COUNT(*) FROM users WHERE id=? AND role='STUDENT' AND active=TRUE", studentId) == 0) throw Access.bad("One of the selected students was not found");
            if (count("SELECT COUNT(*) FROM class_cards WHERE student_id=? AND teacher_id=? AND school_year=? AND semester=? AND subject_code=?",
                    studentId, f.teacherId(), f.schoolYear(), f.semester(), f.subjectCode()) > 0) { skipped++; continue; }
            db.update("INSERT INTO class_cards(student_id,teacher_id,school_year,semester,subject_code,subject_description,units,mode_of_delivery,start_date,end_date,schedule,fee,verify_code,created_by) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                studentId, f.teacherId(), f.schoolYear(), f.semester(), f.subjectCode(), f.description(), f.units(), f.mode(),
                f.start().toString(), f.end().toString(), f.schedule(), f.fee(), newVerifyCode(), me);
            created++;
        }
        String message = "Created " + created + " class card" + (created == 1 ? "" : "s")
            + (skipped > 0 ? " (" + skipped + " already existed and were skipped)" : "");
        return Map.of("message", message, "created", created, "skipped", skipped);
    }

    @PutMapping("/class-cards/{id}")
    Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        List<Map<String, Object>> rows = db.queryForList(LIST_SQL + " WHERE c.id=?", id);
        if (rows.isEmpty()) throw Access.notFound("Class card not found");
        Map<String, Object> current = rows.get(0);
        Fields f = Fields.parse(body, false);
        requireTeacher(f.teacherId());
        if (isPaid(current) && f.fee().compareTo(new BigDecimal(String.valueOf(current.get("fee")))) != 0) {
            throw Access.bad("The fee cannot be changed after the class card has been paid");
        }
        String grade = Access.text(body, "finalGrade");
        String remarks = Access.text(body, "remarks");
        validateGrade(grade, remarks);
        Object finalizedBy = null;
        Object finalizedAt = null;
        if (!grade.isEmpty()) {
            boolean unchanged = grade.equals(String.valueOf(current.get("final_grade")));
            if (unchanged) {
                List<Map<String, Object>> keep = db.queryForList("SELECT finalized_by,DATE_FORMAT(finalized_at,'%Y-%m-%d %H:%i:%s') finalized_at FROM class_cards WHERE id=?", id);
                finalizedBy = keep.get(0).get("finalized_by");
                finalizedAt = keep.get(0).get("finalized_at");
            } else {
                finalizedBy = Access.userId(auth);
                finalizedAt = SQL_TS.format(LocalDateTime.now());
            }
        }
        try {
            db.update("UPDATE class_cards SET teacher_id=?,school_year=?,semester=?,subject_code=?,subject_description=?,units=?,mode_of_delivery=?,"
                + "start_date=?,end_date=?,schedule=?,fee=?,final_grade=?,remarks=?,finalized_by=?,finalized_at=? WHERE id=?",
                f.teacherId(), f.schoolYear(), f.semester(), f.subjectCode(), f.description(), f.units(), f.mode(),
                f.start().toString(), f.end().toString(), f.schedule(), f.fee(),
                grade.isEmpty() ? null : grade, remarks.isEmpty() ? null : remarks, finalizedBy, finalizedAt, id);
        } catch (DuplicateKeyException e) {
            throw Access.conflict("That student already has a class card for this subject, teacher and term");
        }
        return Map.of("message", "Class card updated");
    }

    @DeleteMapping("/class-cards/{id}")
    Map<String, Object> delete(@PathVariable long id, Authentication auth) {
        Access.require(auth, "ADMIN");
        try {
            if (db.update("DELETE FROM class_cards WHERE id=?", id) == 0) throw Access.notFound("Class card not found");
        } catch (DataIntegrityViolationException e) {
            throw Access.conflict("This class card has a recorded payment, so it cannot be deleted.");
        }
        return Map.of("message", "Class card deleted");
    }

    // ------------------------------------------------------------------ cashier: payment for one class card

    @PostMapping("/class-cards/{id}/payment")
    Map<String, Object> pay(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "CASHIER", "ADMIN");
        List<Map<String, Object>> rows = db.queryForList(LIST_SQL + " WHERE c.id=?", id);
        if (rows.isEmpty()) throw Access.notFound("Class card not found");
        Map<String, Object> c = rows.get(0);
        if (isPaid(c)) throw Access.conflict("This class card has already been paid");
        String method = Access.text(body, "paymentMethod").toUpperCase(Locale.ROOT);
        if (method.isEmpty()) method = "CASH";
        if (!METHODS.contains(method)) throw Access.bad("Unsupported payment method");
        String or = Access.text(body, "officialReceiptNo");
        if (!or.isEmpty() && !OR_NUMBER.matcher(or).matches()) throw Access.bad("Official receipt number may only contain letters, numbers and dashes");
        BigDecimal amount = new BigDecimal(String.valueOf(c.get("fee")));      // always the class card fee, never a client value
        String reference = "CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        try {
            db.update("INSERT INTO class_card_payments(class_card_id,amount,reference_no,or_number,payment_method,recorded_by) VALUES (?,?,?,?,?,?)",
                id, amount, reference, or.isEmpty() ? null : or, method, Access.userId(auth));
        } catch (DuplicateKeyException e) {
            throw Access.conflict("That official receipt number is already used, or this class card was just paid");
        }
        return Map.of("message", "Class card payment recorded for " + c.get("student_name"), "referenceNo", reference, "amount", amount);
    }

    // ------------------------------------------------------------------ teacher: final grade

    @PostMapping("/class-cards/{id}/grade")
    Map<String, Object> grade(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "TEACHER", "ADMIN");
        List<Map<String, Object>> rows = db.queryForList("SELECT teacher_id,final_grade FROM class_cards WHERE id=?", id);
        if (rows.isEmpty()) throw Access.notFound("Class card not found");
        if (Access.role(auth).equals("TEACHER")) {
            if (((Number) rows.get(0).get("teacher_id")).longValue() != Access.userId(auth)) throw Access.notFound("Class card not found");
            if (rows.get(0).get("final_grade") != null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This grade is already finalized. Ask an administrator to change it.");
            }
        }
        String grade = Access.text(body, "finalGrade");
        String remarks = Access.text(body, "remarks");
        if (grade.isEmpty()) throw Access.bad("Enter the final grade");
        validateGrade(grade, remarks);
        db.update("UPDATE class_cards SET final_grade=?,remarks=?,finalized_by=?,finalized_at=? WHERE id=?",
            grade, remarks.isEmpty() ? null : remarks, Access.userId(auth), SQL_TS.format(LocalDateTime.now()), id);
        return Map.of("message", "Final grade submitted");
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isPaid(Map<String, Object> row) {
        Object v = row.get("paid");
        if (v instanceof Boolean b) return b;
        return v instanceof Number n && n.longValue() != 0;
    }

    private int count(String sql, Object... args) {
        Integer n = db.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void requireTeacher(long id) {
        if (count("SELECT COUNT(*) FROM users WHERE id=? AND role='TEACHER' AND active=TRUE", id) == 0) throw Access.bad("Choose an active teacher");
    }

    private String newVerifyCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("CC-");
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (count("SELECT COUNT(*) FROM class_cards WHERE verify_code=?", code) == 0) return code;
        }
        throw new IllegalStateException("Could not generate a unique verification code");
    }

    private static void validateGrade(String grade, String remarks) {
        if (!grade.isEmpty() && !GRADE.matcher(grade).matches()) throw Access.bad("Final grade may only contain letters, numbers, dots, plus and minus (up to 10 characters)");
        if (remarks.length() > 255) throw Access.bad("Remarks must be 255 characters or fewer");
    }

    private static List<Long> studentIds(Map<String, Object> body) {
        List<Long> ids = new ArrayList<>();
        Object many = body.get("studentIds");
        try {
            if (many instanceof List<?> list) { for (Object o : list) ids.add(Long.parseLong(String.valueOf(o).trim())); }
            else if (body.get("studentId") != null) ids.add(Long.parseLong(String.valueOf(body.get("studentId")).trim()));
        } catch (NumberFormatException e) {
            throw Access.bad("Student IDs must be numbers");
        }
        if (ids.isEmpty()) throw Access.bad("Select at least one student");
        if (ids.size() > 300) throw Access.bad("Select 300 students or fewer at a time");
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    /** The class card fields shared by create and edit, already validated. */
    private record Fields(long teacherId, String schoolYear, String semester, String subjectCode, String description,
                          BigDecimal units, String mode, LocalDate start, LocalDate end, String schedule, BigDecimal fee) {
        static Fields parse(Map<String, Object> b, boolean requireAll) {
            long teacherId;
            try { teacherId = Long.parseLong(Access.text(b, "teacherId")); } catch (NumberFormatException e) { throw Access.bad("Choose a teacher"); }
            String schoolYear = Access.text(b, "schoolYear");
            String semester = Access.text(b, "semester");
            String code = Access.text(b, "subjectCode");
            String description = Access.text(b, "subjectDescription");
            String mode = Access.text(b, "modeOfDelivery");
            String schedule = Access.text(b, "schedule");
            if (!SCHOOL_YEAR.matcher(schoolYear).matches()) throw Access.bad("School year must look like 2026-2027");
            if (semester.length() < 2 || semester.length() > 30) throw Access.bad("Choose the semester");
            if (!SUBJECT_CODE.matcher(code).matches()) throw Access.bad("Subject code must be 2 to 30 letters, numbers, spaces, dots, dashes or *");
            if (description.length() < 2 || description.length() > 160) throw Access.bad("Subject description must be 2 to 160 characters");
            if (mode.isEmpty()) mode = "F2F";
            if (mode.length() > 40) throw Access.bad("Mode of delivery must be 40 characters or fewer");
            if (schedule.length() > 200) throw Access.bad("Schedule must be 200 characters or fewer");
            BigDecimal units = decimal(Access.text(b, "units").isEmpty() ? "3" : Access.text(b, "units"), "Units", new BigDecimal("0.5"), new BigDecimal("12"), 1);
            BigDecimal fee = decimal(Access.text(b, "fee").isEmpty() ? "0" : Access.text(b, "fee"), "Fee", BigDecimal.ZERO, new BigDecimal("99999.99"), 2);
            LocalDate start;
            LocalDate end;
            try { start = LocalDate.parse(Access.text(b, "startDate")); end = LocalDate.parse(Access.text(b, "endDate")); }
            catch (Exception e) { throw Access.bad("Start and end dates must use YYYY-MM-DD"); }
            if (end.isBefore(start)) throw Access.bad("The end date cannot be before the start date");
            return new Fields(teacherId, schoolYear, semester, code, description, units, mode, start, end, schedule.isEmpty() ? null : schedule, fee);
        }

        private static BigDecimal decimal(String s, String label, BigDecimal min, BigDecimal max, int scale) {
            try {
                BigDecimal d = new BigDecimal(s);
                if (d.compareTo(min) < 0 || d.compareTo(max) > 0 || d.scale() > scale) throw new NumberFormatException();
                return d;
            } catch (NumberFormatException e) {
                throw Access.bad(label + " must be between " + min + " and " + max + " with at most " + scale + " decimal place" + (scale == 1 ? "" : "s"));
            }
        }
    }
}
