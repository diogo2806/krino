package br.com.krino.audit;

import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SecurityAuditService {

    private static final int MAX_DETAILS_LENGTH = 1000;
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(password|senha|token|secret|segredo|credential|credencial|authorization|bearer)(\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?|\\s+)([^\\s,;\\\"']+)");

    private final JdbcTemplate jdbcTemplate;

    public SecurityAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String actorUsername, String action, String targetType, String targetReference, String details) {
        jdbcTemplate.update(
                "insert into security_audit_event (actor_username, action, target_type, target_reference, details) values (?, ?, ?, ?, ?)",
                actorUsername == null || actorUsername.isBlank() ? "SYSTEM" : actorUsername,
                action,
                targetType,
                targetReference,
                sanitizeDetails(details));
    }

    private String sanitizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return details;
        }
        String sanitized = SENSITIVE_VALUE.matcher(details).replaceAll("$1=[REDACTED]");
        return sanitized.length() <= MAX_DETAILS_LENGTH ? sanitized : sanitized.substring(0, MAX_DETAILS_LENGTH);
    }
}
