package br.com.krino.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    private final JdbcTemplate jdbcTemplate;

    public AuditQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AuditEventView> search(Instant from, Instant to, String actor, String action, Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        StringBuilder sql = new StringBuilder("select id, actor_username, action, target_type, target_reference, details, created_at from security_audit_event where 1 = 1");
        List<Object> params = new ArrayList<>();

        if (from != null) {
            sql.append(" and created_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and created_at <= ?");
            params.add(Timestamp.from(to));
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" and lower(actor_username) like lower(?)");
            params.add("%" + actor.trim() + "%");
        }
        if (action != null && !action.isBlank()) {
            sql.append(" and lower(action) like lower(?)");
            params.add("%" + action.trim() + "%");
        }

        sql.append(" order by created_at desc, id desc limit ?");
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AuditEventView(
                rs.getLong("id"),
                rs.getString("actor_username"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_reference"),
                rs.getString("details"),
                rs.getObject("created_at", OffsetDateTime.class)), params.toArray());
    }

    public record AuditEventView(
            long id,
            String actorUsername,
            String action,
            String targetType,
            String targetReference,
            String details,
            OffsetDateTime createdAt) {
    }
}
