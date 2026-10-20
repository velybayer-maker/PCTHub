package com.pcthub;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Announcement board, event calendar, student clearance and admin-only task edit/delete.
 * Everyone who is signed in can READ. Only ADMIN can create, edit or delete.
 */
@RestController
@RequestMapping("/api")
class CampusController {
    private static final DateTimeFormatter SQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String STAMP = "%Y-%m-%d %H:%i:%s";
    private static final String ISO = "%Y-%m-%dT%H:%i:%s";
    private static final Set<String> AUDIENCES = Set.of("ALL", "STUDENT", "TEACHER");
    private static final Set<String> CLEARANCE_STATUSES = Set.of("PENDING", "CLEARED", "HOLD");

    private final JdbcTemplate db;
    CampusController(JdbcTemplate db) { this.db = db; }

    // ------------------------------------------------------------------ announcements

    @GetMapping("/announcements")
    List<Map<String, Object>> announcements(Authentication auth) {
        String where = switch (Access.role(auth)) {
            case "STUDENT" -> " WHERE a.audience IN ('ALL','STUDENT')";
            case "TEACHER" -> " WHERE a.audience IN ('ALL','TEACHER')";
            case "ADMIN" -> "";
            default -> " WHERE a.audience='ALL'";
        };
        return db.queryForList("SELECT a.id,a.title,a.body,a.audience,a.pinned,"
            + "DATE_FORMAT(a.created_at,'" + STAMP + "') created_at,DATE_FORMAT(a.updated_at,'" + STAMP + "') updated_at,"
            + "u.full_name author FROM announcements a JOIN users u ON u.id=a.created_by" + where
            + " ORDER BY a.pinned DESC,a.created_at DESC,a.id DESC LIMIT 200");
    }

    @PostMapping("/announcements")
    Map<String, Object> createAnnouncement(@RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        String title = Access.text(body, "title");
        String content = Access.text(body, "body");
        String audience = audienceOf(body);
        validateAnnouncement(title, content);
        db.update("INSERT INTO announcements(title,body,audience,pinned,created_by) VALUES (?,?,?,?,?)",
            title, content, audience, Access.flag(body.get("pinned")), Access.userId(auth));
        return Map.of("message", "Announcement posted");
    }

    @PutMapping("/announcements/{id}")
    Map<String, Object> updateAnnouncement(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        String title = Access.text(body, "title");
        String content = Access.text(body, "body");
        String audience = audienceOf(body);
        validateAnnouncement(title, content);
        int changed = db.update("UPDATE announcements SET title=?,body=?,audience=?,pinned=? WHERE id=?",
            title, content, audience, Access.flag(body.get("pinned")), id);
        if (changed == 0) throw Access.notFound("Announcement not found");
        return Map.of("message", "Announcement updated");
    }

    @DeleteMapping("/announcements/{id}")
    Map<String, Object> deleteAnnouncement(@PathVariable long id, Authentication auth) {
        Access.require(auth, "ADMIN");
        if (db.update("DELETE FROM announcements WHERE id=?", id) == 0) throw Access.notFound("Announcement not found");
        return Map.of("message", "Announcement deleted");
    }

    private static String audienceOf(Map<String, Object> body) {
        String audience = Access.text(body, "audience").toUpperCase(Locale.ROOT);
        if (audience.isEmpty()) audience = "ALL";
        if (!AUDIENCES.contains(audience)) throw Access.bad("Audience must be ALL, STUDENT or TEACHER");
        return audience;
    }

    private static void validateAnnouncement(String title, String content) {
        if (title.isBlank() || content.isBlank()) throw Access.bad("Title and message are required");
        if (title.length() > 180) throw Access.bad("Title must be 180 characters or fewer");
        if (content.length() > 5000) throw Access.bad("Message must be 5000 characters or fewer");
    }

    // ------------------------------------------------------------------ calendar

    @GetMapping("/events")
    List<Map<String, Object>> events(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        String sql = "SELECT e.id,e.title,e.description,e.location,DATE_FORMAT(e.start_at,'" + ISO + "') start_at,"
            + "DATE_FORMAT(e.end_at,'" + ISO + "') end_at,u.full_name created_by "
            + "FROM calendar_events e JOIN users u ON u.id=e.created_by";
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            LocalDate start;
            LocalDate end;
            try {
                start = LocalDate.parse(from.trim());
                end = LocalDate.parse(to.trim());
            } catch (Exception ex) {
                throw Access.bad("from and to must use YYYY-MM-DD");
            }
            return db.queryForList(sql + " WHERE e.start_at >= ? AND e.start_at < ? ORDER BY e.start_at,e.id LIMIT 1000",
                start + " 00:00:00", end.plusDays(1) + " 00:00:00");
        }
        return db.queryForList(sql + " ORDER BY e.start_at,e.id LIMIT 1000");
    }

    @PostMapping("/events")
    Map<String, Object> createEvent(@RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        Object[] f = eventFields(body);
        db.update("INSERT INTO calendar_events(title,description,location,start_at,end_at,created_by) VALUES (?,?,?,?,?,?)",
            f[0], f[1], f[2], f[3], f[4], Access.userId(auth));
        return Map.of("message", "Event added to the calendar");
    }

    @PutMapping("/events/{id}")
    Map<String, Object> updateEvent(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        Object[] f = eventFields(body);
        int changed = db.update("UPDATE calendar_events SET title=?,description=?,location=?,start_at=?,end_at=? WHERE id=?",
            f[0], f[1], f[2], f[3], f[4], id);
        if (changed == 0) throw Access.notFound("Event not found");
        return Map.of("message", "Event updated");
    }

    @DeleteMapping("/events/{id}")
    Map<String, Object> deleteEvent(@PathVariable long id, Authentication auth) {
        Access.require(auth, "ADMIN");
        if (db.update("DELETE FROM calendar_events WHERE id=?", id) == 0) throw Access.notFound("Event not found");
        return Map.of("message", "Event deleted");
    }

    /** Returns {title, description, location, startAt, endAt} ready for the SQL insert/update. */
    private static Object[] eventFields(Map<String, Object> body) {
        String title = Access.text(body, "title");
        String description = Access.text(body, "description");
        String location = Access.text(body, "location");
        if (title.isBlank()) throw Access.bad("Event title is required");
        if (title.length() > 180) throw Access.bad("Title must be 180 characters or fewer");
        if (location.length() > 160) throw Access.bad("Location must be 160 characters or fewer");
        if (description.length() > 2000) throw Access.bad("Description must be 2000 characters or fewer");
        LocalDateTime start = parseDateTime(Access.text(body, "startAt"), "Start date and time");
        String endRaw = Access.text(body, "endAt");
        LocalDateTime end = endRaw.isEmpty() ? null : parseDateTime(endRaw, "End date and time");
        if (end != null && end.isBefore(start)) throw Access.bad("The event cannot end before it starts");
        return new Object[]{title, description.isEmpty() ? null : description, location.isEmpty() ? null : location,
            SQL_TS.format(start), end == null ? null : SQL_TS.format(end)};
    }

    private static LocalDateTime parseDateTime(String value, String label) {
        try { return LocalDateTime.parse(value); }
        catch (Exception e) { throw Access.bad(label + " must use YYYY-MM-DDTHH:MM format"); }
    }

    // ------------------------------------------------------------------ clearance

    @GetMapping("/clearance/me")
    Map<String, Object> myClearance(Authentication auth) {
        Access.require(auth, "STUDENT");
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT i.id item_id,i.name,i.description,COALESCE(c.status,'PENDING') status,c.remarks,"
            + "DATE_FORMAT(c.updated_at,'" + STAMP + "') updated_at "
            + "FROM clearance_items i LEFT JOIN student_clearances c ON c.item_id=i.id AND c.student_id=? "
            + "ORDER BY i.sort_order,i.id", Access.userId(auth));
        int cleared = 0;
        int hold = 0;
        for (Map<String, Object> r : rows) {
            String status = String.valueOf(r.get("status"));
            if (status.equals("CLEARED")) cleared++;
            else if (status.equals("HOLD")) hold++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", rows);
        out.put("total", rows.size());
        out.put("cleared", cleared);
        out.put("overall", overall(cleared, hold, rows.size()));
        return out;
    }

    @GetMapping("/clearance")
    Map<String, Object> clearanceBoard(Authentication auth) {
        Access.require(auth, "ADMIN");
        List<Map<String, Object>> items = db.queryForList("SELECT id,name,description FROM clearance_items ORDER BY sort_order,id");
        List<Map<String, Object>> students = db.queryForList("SELECT id,full_name,student_number FROM users WHERE role='STUDENT' AND active=TRUE ORDER BY full_name");
        Map<String, Map<String, Object>> cells = new HashMap<>();
        for (Map<String, Object> r : db.queryForList("SELECT student_id,item_id,status,remarks FROM student_clearances")) {
            cells.put(r.get("student_id") + ":" + r.get("item_id"), r);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> s : students) {
            int cleared = 0;
            int hold = 0;
            Map<String, Object> statuses = new LinkedHashMap<>();
            for (Map<String, Object> it : items) {
                Map<String, Object> cell = cells.get(s.get("id") + ":" + it.get("id"));
                String status = cell == null ? "PENDING" : String.valueOf(cell.get("status"));
                Object remarks = cell == null || cell.get("remarks") == null ? "" : cell.get("remarks");
                if (status.equals("CLEARED")) cleared++;
                else if (status.equals("HOLD")) hold++;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("status", status);
                c.put("remarks", remarks);
                statuses.put(String.valueOf(it.get("id")), c);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", s.get("id"));
            row.put("fullName", s.get("full_name"));
            row.put("studentNumber", s.get("student_number"));
            row.put("statuses", statuses);
            row.put("overall", overall(cleared, hold, items.size()));
            out.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("students", out);
        return result;
    }

    @PutMapping("/clearance/{studentId}/{itemId}")
    Map<String, Object> setClearance(@PathVariable long studentId, @PathVariable long itemId,
                                     @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        String status = Access.text(body, "status").toUpperCase(Locale.ROOT);
        String remarks = Access.text(body, "remarks");
        if (!CLEARANCE_STATUSES.contains(status)) throw Access.bad("Status must be PENDING, CLEARED or HOLD");
        if (remarks.length() > 255) throw Access.bad("Remarks must be 255 characters or fewer");
        requireStudent(studentId);
        requireItem(itemId);
        db.update("INSERT INTO student_clearances(student_id,item_id,status,remarks,updated_by) VALUES (?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE status=VALUES(status),remarks=VALUES(remarks),updated_by=VALUES(updated_by)",
            studentId, itemId, status, remarks.isEmpty() ? null : remarks, Access.userId(auth));
        return Map.of("message", "Clearance updated");
    }

    @PutMapping("/clearance-all/{studentId}")
    Map<String, Object> setAllClearance(@PathVariable long studentId, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        String status = Access.text(body, "status").toUpperCase(Locale.ROOT);
        if (!CLEARANCE_STATUSES.contains(status)) throw Access.bad("Status must be PENDING, CLEARED or HOLD");
        requireStudent(studentId);
        db.update("INSERT INTO student_clearances(student_id,item_id,status,updated_by) SELECT ?,id,?,? FROM clearance_items "
            + "ON DUPLICATE KEY UPDATE status=VALUES(status),updated_by=VALUES(updated_by)",
            studentId, status, Access.userId(auth));
        return Map.of("message", "All requirements marked " + status.toLowerCase(Locale.ROOT));
    }

    @PostMapping("/clearance-items")
    Map<String, Object> createClearanceItem(@RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        String name = Access.text(body, "name");
        String description = Access.text(body, "description");
        validateItem(name, description);
        try {
            db.update("INSERT INTO clearance_items(name,description,sort_order) SELECT ?,?,COALESCE(MAX(sort_order),0)+1 FROM clearance_items",
                name, description.isEmpty() ? null : description);
        } catch (DataIntegrityViolationException e) {
            throw Access.conflict("A clearance requirement with that name already exists");
        }
        return Map.of("message", "Clearance requirement added");
    }

    @PutMapping("/clearance-items/{id}")
    Map<String, Object> updateClearanceItem(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        String name = Access.text(body, "name");
        String description = Access.text(body, "description");
        validateItem(name, description);
        int changed;
        try {
            changed = db.update("UPDATE clearance_items SET name=?,description=? WHERE id=?", name, description.isEmpty() ? null : description, id);
        } catch (DataIntegrityViolationException e) {
            throw Access.conflict("A clearance requirement with that name already exists");
        }
        if (changed == 0) throw Access.notFound("Clearance requirement not found");
        return Map.of("message", "Clearance requirement updated");
    }

    @DeleteMapping("/clearance-items/{id}")
    Map<String, Object> deleteClearanceItem(@PathVariable long id, Authentication auth) {
        Access.require(auth, "ADMIN");
        if (db.update("DELETE FROM clearance_items WHERE id=?", id) == 0) throw Access.notFound("Clearance requirement not found");
        return Map.of("message", "Clearance requirement deleted");
    }

    private static void validateItem(String name, String description) {
        if (name.length() < 2 || name.length() > 120) throw Access.bad("Requirement name must be 2 to 120 characters");
        if (description.length() > 255) throw Access.bad("Description must be 255 characters or fewer");
    }

    private static String overall(int cleared, int hold, int total) {
        if (total == 0) return "NOT_CONFIGURED";
        if (cleared == total) return "CLEARED";
        if (hold > 0) return "HOLD";
        return "IN_PROGRESS";
    }

    private void requireStudent(long id) {
        Integer n = db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='STUDENT'", Integer.class, id);
        if (n == null || n == 0) throw Access.notFound("Student not found");
    }

    private void requireItem(long id) {
        Integer n = db.queryForObject("SELECT COUNT(*) FROM clearance_items WHERE id=?", Integer.class, id);
        if (n == null || n == 0) throw Access.notFound("Clearance requirement not found");
    }

    // ------------------------------------------------------------------ academic tasks (admin edit / delete)

    @PutMapping("/tasks/{id}")
    Map<String, Object> updateTask(@PathVariable long id, @RequestBody Map<String, Object> body, Authentication auth) {
        Access.require(auth, "ADMIN");
        String title = Access.text(body, "title");
        String subject = Access.text(body, "subject");
        String description = Access.text(body, "description");
        if (title.isBlank() || subject.isBlank()) throw Access.bad("Title and subject are required");
        LocalDateTime due = parseDateTime(Access.text(body, "dueAt"), "Due date");
        int changed = db.update("UPDATE academic_tasks SET title=?,description=?,subject=?,due_at=? WHERE id=?",
            title, description, subject, SQL_TS.format(due), id);
        if (changed == 0) throw Access.notFound("Task not found");
        return Map.of("message", "Academic task updated");
    }

    @DeleteMapping("/tasks/{id}")
    Map<String, Object> deleteTask(@PathVariable long id, Authentication auth) {
        Access.require(auth, "ADMIN");
        if (db.update("DELETE FROM academic_tasks WHERE id=?", id) == 0) throw Access.notFound("Task not found");
        return Map.of("message", "Academic task deleted");
    }
}
