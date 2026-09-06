package br.com.krino.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SecurityAuditService {

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
                details);
    }
}
