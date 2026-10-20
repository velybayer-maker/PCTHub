package com.pcthub;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Small shared helpers for role checks and request validation. */
final class Access {
    private Access() {}

    static String role(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .findFirst().orElse("");
    }

    static boolean has(Authentication auth, String... roles) {
        return Arrays.asList(roles).contains(role(auth));
    }

    static void require(Authentication auth, String... roles) {
        if (has(auth, roles)) return;
        boolean adminOnly = roles.length == 1 && roles[0].equals("ADMIN");
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            adminOnly ? "Only administrators can add, edit or delete this." : "Your role is not allowed to perform this action");
    }

    static long userId(Authentication auth) { return Long.parseLong(auth.getName()); }

    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    static String text(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    static boolean flag(Object v) {
        if (v instanceof Boolean b) return b;
        return v != null && Set.of("true", "1", "on", "yes").contains(String.valueOf(v).trim().toLowerCase(Locale.ROOT));
    }
}
