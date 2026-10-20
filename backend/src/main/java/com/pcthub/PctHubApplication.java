    package com.pcthub;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@SpringBootApplication
public class PctHubApplication {
    public static void main(String[] args) { SpringApplication.run(PctHubApplication.class, args); }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean SecretKey jwtKey() {
        String secret = System.getenv().getOrDefault("JWT_SECRET", "local-dev-only-secret-change-this-123456789");
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .anyRequest().permitAll()
        );

    return http.build();
}

    @Bean CommandLineRunner seedDemo(JdbcTemplate db, PasswordEncoder encoder) {
        return args -> {
            if (!"true".equalsIgnoreCase(System.getenv().getOrDefault("APP_DEMO_SEED", "true"))) return;
            seedUser(db, encoder, "admin", "Admin Demo", "ADMIN", null);
            seedUser(db, encoder, "cashier", "Cashier Demo", "CASHIER", null);
            seedUser(db, encoder, "teacher", "Teacher Demo", "TEACHER", null);
            seedUser(db, encoder, "student", "Student Demo", "STUDENT", "PCT-2026-001");
            Long studentId = db.queryForObject("SELECT id FROM users WHERE username='student'", Long.class);
            db.update("INSERT IGNORE INTO student_accounts(user_id,total_fees) VALUES (?,?)", studentId, new BigDecimal("25000.00"));
            Integer taskCount = db.queryForObject("SELECT COUNT(*) FROM academic_tasks", Integer.class);
            if (taskCount != null && taskCount == 0) {
                Long teacherId = db.queryForObject("SELECT id FROM users WHERE username='teacher'", Long.class);
                db.update("INSERT INTO academic_tasks(title,description,subject,due_at,created_by) VALUES (?,?,?,?,?)",
                    "Sample programming assignment", "Create a simple program and explain its logic.", "Programming", Timestamp.valueOf(LocalDateTime.now().plusDays(7)), teacherId);
                db.update("INSERT INTO academic_tasks(title,description,subject,due_at,created_by) VALUES (?,?,?,?,?)",
                    "Database normalization exercise", "Identify entities and normalize the provided sample data.", "Database Management", Timestamp.valueOf(LocalDateTime.now().plusDays(14)), teacherId);
            }
        };
    }

    private static void seedUser(JdbcTemplate db, PasswordEncoder encoder, String username, String name, String role, String studentNo) {
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM users WHERE username=?", Integer.class, username);
        if (exists == null || exists == 0) {
            db.update("INSERT INTO users(username,password_hash,full_name,role,student_number) VALUES (?,?,?,?,?)",
                username, encoder.encode("PCTHub123!"), name, role, studentNo);
        }
    }
}

@RestController
@RequestMapping("/api")
class ApiController {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final SecretKey jwtKey;
    ApiController(JdbcTemplate db, PasswordEncoder encoder, SecretKey jwtKey) { this.db=db; this.encoder=encoder; this.jwtKey=jwtKey; }

    @GetMapping("/health") Map<String,Object> health() { return Map.of("status", "ok", "service", "pcthub-java-api"); }

    @PostMapping("/auth/login") Map<String,Object> login(@RequestBody Map<String,String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        List<Map<String,Object>> rows = db.queryForList("SELECT id,username,password_hash,full_name,role,student_number,active FROM users WHERE username=?", username);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("active")) || !encoder.matches(password, (String)rows.get(0).get("password_hash"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Map<String,Object> user = rows.get(0);
        String token = Jwts.builder().subject(String.valueOf(user.get("id")))
            .claim("role", user.get("role")).claim("username", user.get("username"))
            .issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(60 * 60 * 8)))
            .signWith(jwtKey).compact();
        return Map.of("token", token, "expiresIn", 28800, "user", Map.of(
            "id", user.get("id"), "username", user.get("username"), "fullName", user.get("full_name"),
            "role", user.get("role"), "studentNumber", user.get("student_number") == null ? "" : user.get("student_number")));
    }

    @GetMapping("/me") Map<String,Object> me(Authentication auth) {
        return db.queryForMap("SELECT id,username,full_name,role,COALESCE(student_number,'') student_number FROM users WHERE id=?", Long.parseLong(auth.getName()));
    }

    @GetMapping("/payments") List<Map<String,Object>> payments(Authentication auth) {
        boolean staff = hasRole(auth, "CASHIER", "ADMIN");
        String sql = "SELECT p.id,p.student_id,u.full_name student_name,u.student_number,p.amount,p.reference_no,p.payment_method,p.notes,p.paid_at,rec.full_name recorded_by FROM payments p JOIN users u ON u.id=p.student_id JOIN users rec ON rec.id=p.recorded_by";
        if (staff) return db.queryForList(sql + " ORDER BY p.paid_at DESC LIMIT 500");
        return db.queryForList(sql + " WHERE p.student_id=? ORDER BY p.paid_at DESC", Long.parseLong(auth.getName()));
    }

    @GetMapping("/payments/summary") Map<String,Object> paymentSummary(Authentication auth) {
        boolean staff = hasRole(auth, "CASHIER", "ADMIN");
        String where = staff ? "" : " WHERE u.id=?";
        List<Map<String,Object>> rows = staff
            ? db.queryForList("SELECT u.id,u.full_name,u.student_number,COALESCE(a.total_fees,0) total_fees,COALESCE(SUM(p.amount),0) paid FROM users u LEFT JOIN student_accounts a ON a.user_id=u.id LEFT JOIN payments p ON p.student_id=u.id WHERE u.role='STUDENT' GROUP BY u.id ORDER BY u.full_name")
            : db.queryForList("SELECT u.id,u.full_name,u.student_number,COALESCE(a.total_fees,0) total_fees,COALESCE(SUM(p.amount),0) paid FROM users u LEFT JOIN student_accounts a ON a.user_id=u.id LEFT JOIN payments p ON p.student_id=u.id WHERE u.id=? GROUP BY u.id", Long.parseLong(auth.getName()));
        List<Map<String,Object>> results = new ArrayList<>();
        BigDecimal totalFees=BigDecimal.ZERO, totalPaid=BigDecimal.ZERO, totalBalance=BigDecimal.ZERO;
        for (Map<String,Object> r : rows) {
            BigDecimal fees = money(r.get("total_fees")); BigDecimal paid = money(r.get("paid")); BigDecimal balance = fees.subtract(paid).max(BigDecimal.ZERO);
            Map<String,Object> item = new LinkedHashMap<>(); item.put("studentId",r.get("id")); item.put("studentName",r.get("full_name")); item.put("studentNumber",r.get("student_number")); item.put("totalFees",fees); item.put("paid",paid); item.put("balance",balance); results.add(item);
            totalFees=totalFees.add(fees); totalPaid=totalPaid.add(paid); totalBalance=totalBalance.add(balance);
        }
        if (!staff) return results.isEmpty() ? Map.of("totalFees",0,"paid",0,"balance",0) : results.get(0);
        return Map.of("students",results,"totalFees",totalFees,"paid",totalPaid,"balance",totalBalance);
    }

    @PostMapping("/payments") Map<String,Object> recordPayment(@RequestBody Map<String,Object> body, Authentication auth) {
        requireRole(auth, "CASHIER", "ADMIN");
        long studentId = number(body.get("studentId"), "studentId");
        BigDecimal amount;
        try { amount = new BigDecimal(String.valueOf(body.get("amount"))); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Amount must be a valid number"); }
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.scale() > 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Amount must be positive and have at most two decimal places");
        if (db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='STUDENT'",Integer.class,studentId)==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Student account not found");
        String ref = "PCT-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        String method = String.valueOf(body.getOrDefault("paymentMethod","CASH")).toUpperCase(Locale.ROOT);
        if (!Set.of("CASH","BANK_TRANSFER","GCASH","OTHER").contains(method)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported payment method");
        String notes = String.valueOf(body.getOrDefault("notes",""));
        db.update("INSERT INTO payments(student_id,amount,reference_no,payment_method,notes,recorded_by) VALUES (?,?,?,?,?,?)",studentId,amount,ref,method,notes.isBlank()?null:notes,Long.parseLong(auth.getName()));
        return Map.of("message","Payment recorded","referenceNo",ref,"amount",amount);
    }

    @GetMapping("/students") List<Map<String,Object>> students(Authentication auth) {
        requireRole(auth,"CASHIER","ADMIN","TEACHER");
        return db.queryForList("SELECT id,full_name,student_number,username FROM users WHERE role='STUDENT' AND active=TRUE ORDER BY full_name");
    }

    @GetMapping("/tasks") List<Map<String,Object>> tasks(Authentication auth) {
        if (hasRole(auth,"STUDENT")) {
            return db.queryForList("SELECT t.id,t.title,t.description,t.subject,t.due_at,creator.full_name created_by,s.status submission_status,s.submitted_at FROM academic_tasks t JOIN users creator ON creator.id=t.created_by LEFT JOIN submissions s ON s.task_id=t.id AND s.student_id=? ORDER BY t.due_at", Long.parseLong(auth.getName()));
        }
        return db.queryForList("SELECT t.id,t.title,t.description,t.subject,t.due_at,creator.full_name created_by,COUNT(s.id) submissions FROM academic_tasks t JOIN users creator ON creator.id=t.created_by LEFT JOIN submissions s ON s.task_id=t.id GROUP BY t.id ORDER BY t.due_at");
    }

    @PostMapping("/tasks") Map<String,Object> createTask(@RequestBody Map<String,String> body, Authentication auth) {
        requireRole(auth,"TEACHER","ADMIN");
        String title = body.getOrDefault("title", "").trim(); String subject = body.getOrDefault("subject", "").trim(); String due = body.getOrDefault("dueAt", "").trim();
        if (title.isBlank() || subject.isBlank() || due.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Title, subject, and due date are required");
        LocalDateTime dueAt;
        try { dueAt = LocalDateTime.parse(due); } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Due date must use YYYY-MM-DDTHH:MM format"); }
        db.update("INSERT INTO academic_tasks(title,description,subject,due_at,created_by) VALUES (?,?,?,?,?)",title,body.getOrDefault("description",""),subject,Timestamp.valueOf(dueAt),Long.parseLong(auth.getName()));
        return Map.of("message","Academic task created");
    }

    @PostMapping("/tasks/{taskId}/submissions") Map<String,Object> submitTask(@PathVariable long taskId, @RequestBody Map<String,String> body, Authentication auth) {
        requireRole(auth,"STUDENT");
        String submission = body.getOrDefault("submissionText", "").trim();
        if (submission.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Submission text is required");
        if (db.queryForObject("SELECT COUNT(*) FROM academic_tasks WHERE id=?",Integer.class,taskId)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Task not found");
        db.update("INSERT INTO submissions(task_id,student_id,submission_text,status) VALUES (?,?,?,'SUBMITTED') ON DUPLICATE KEY UPDATE submission_text=VALUES(submission_text),status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP",taskId,Long.parseLong(auth.getName()),submission);
        return Map.of("message","Task submission saved");
    }

    @GetMapping("/reports") Map<String,Object> reports(Authentication auth) {
        requireRole(auth,"CASHIER","ADMIN","TEACHER");
        Map<String,Object> payments = db.queryForMap("SELECT COUNT(*) payment_count,COALESCE(SUM(amount),0) collected FROM payments");
        Map<String,Object> tasks = db.queryForMap("SELECT COUNT(*) task_count,(SELECT COUNT(*) FROM submissions) submission_count FROM academic_tasks");
        return Map.of("payments",payments,"academic",tasks,"balances",paymentSummary(auth));
    }

    private static BigDecimal money(Object v) { return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()); }
    private static long number(Object v, String field) { try { return Long.parseLong(String.valueOf(v)); } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,field+" must be a valid ID"); } }
    private static boolean hasRole(Authentication auth, String... roles) { return Arrays.stream(roles).anyMatch(r -> auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_"+r))); }
    private static void requireRole(Authentication auth, String... roles) { if (!hasRole(auth,roles)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Your role is not allowed to perform this action"); }
}

@org.springframework.stereotype.Component
class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final SecretKey jwtKey;
    JwtAuthenticationFilter(SecretKey jwtKey) { this.jwtKey=jwtKey; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return request.getRequestURI().equals("/api/auth/login") || request.getRequestURI().equals("/api/health"); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header=request.getHeader("Authorization");
        if (header==null || !header.startsWith("Bearer ")) { response.sendError(401,"Bearer token required"); return; }
        try {
            var claims=Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(header.substring(7)).getPayload();
            String id=claims.getSubject(); String role=String.valueOf(claims.get("role"));
            var auth=new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request,response);
        } catch (JwtException | IllegalArgumentException ex) {
            response.sendError(401,"Invalid or expired token");
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    org.springframework.http.ResponseEntity<Map<String,String>> status(ResponseStatusException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()==null?"Request failed":ex.getReason()));
    }
}
