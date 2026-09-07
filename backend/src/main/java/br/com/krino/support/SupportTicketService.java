package br.com.krino.support;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportTicketService {

    private static final String TICKET_SELECT = "select t.id, t.opened_by_user_id, u.username requester_username, u.display_name requester_name, t.subject, t.description, t.severity, t.status, t.resolution, t.opened_at, t.first_support_response_at, t.resolved_at, t.closed_at, t.updated_at from support_ticket t join app_user u on u.id = t.opened_by_user_id";

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;
    private final SecurityAuditService auditService;

    public SupportTicketService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<TicketView> listOwn(Authentication authentication, String status, String severity, String search) {
        KrinoUserPrincipal principal = principal(authentication);
        return searchTickets("t.opened_by_user_id = ?", principal.id(), status, severity, search);
    }

    public List<TicketView> listAll(String status, String severity, String search) {
        return searchTickets("1 = 1", null, status, severity, search);
    }

    public TicketDetail detail(long ticketId, Authentication authentication) {
        TicketView ticket = ticket(ticketId);
        requireTicketAccess(ticket, authentication);
        return new TicketDetail(ticket, events(ticketId));
    }

    @Transactional
    public TicketDetail create(CreateTicketRequest request, Authentication authentication) {
        KrinoUserPrincipal principal = principal(authentication);
        String subject = request.subject().trim();
        String description = request.description().trim();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    "insert into support_ticket (opened_by_user_id, subject, description, severity) values (?, ?, ?, ?)",
                    new String[]{"id"});
            statement.setLong(1, principal.id());
            statement.setString(2, subject);
            statement.setString(3, description);
            statement.setString(4, request.severity().name());
            return statement;
        }, keyHolder);
        long ticketId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        addEvent(ticketId, principal, "CREATED", null, request.severity().name(), "Chamado aberto pelo usuário.");
        auditService.record(principal.username(), "SUPPORT_TICKET_CREATED", "SUPPORT_TICKET", Long.toString(ticketId), "Chamado aberto com criticidade " + request.severity().name() + ".");
        return new TicketDetail(ticket(ticketId), events(ticketId));
    }

    @Transactional
    public TicketDetail addMessage(long ticketId, MessageRequest request, Authentication authentication) {
        TicketView ticket = ticket(ticketId);
        requireTicketAccess(ticket, authentication);
        if (ticket.status() == TicketStatus.CLOSED) {
            throw new IllegalArgumentException("Chamado encerrado não aceita novas interações.");
        }
        KrinoUserPrincipal principal = principal(authentication);
        boolean manager = authorizationService.hasNetworkPermission(authentication, "SUPPORT_TICKET_MANAGE");
        addEvent(ticketId, principal, "MESSAGE", null, null, request.message().trim());
        if (shouldRegisterFirstSupportResponse(manager, ticket.firstSupportResponseAt())) {
            jdbcTemplate.update("update support_ticket set first_support_response_at = current_timestamp, updated_at = current_timestamp where id = ?", ticketId);
        } else {
            jdbcTemplate.update("update support_ticket set updated_at = current_timestamp where id = ?", ticketId);
        }
        auditService.record(principal.username(), "SUPPORT_TICKET_MESSAGE_ADDED", "SUPPORT_TICKET", Long.toString(ticketId), manager ? "Interação registrada pela equipe de suporte." : "Interação registrada pelo solicitante.");
        return new TicketDetail(ticket(ticketId), events(ticketId));
    }

    @Transactional
    public TicketDetail manage(long ticketId, ManageTicketRequest request, Authentication authentication) {
        TicketView current = ticket(ticketId);
        String resolution = normalize(request.resolution());
        validateManagement(current.status(), request.status(), resolution);

        KrinoUserPrincipal principal = principal(authentication);
        if (current.severity() != request.severity()) {
            addEvent(ticketId, principal, "SEVERITY_CHANGED", current.severity().name(), request.severity().name(), "Criticidade alterada pela equipe de suporte.");
        }
        if (current.status() != request.status()) {
            addEvent(ticketId, principal, "STATUS_CHANGED", current.status().name(), request.status().name(), "Status alterado pela equipe de suporte.");
        }
        if (!Objects.equals(current.resolution(), resolution)) {
            addEvent(ticketId, principal, "RESOLUTION_RECORDED", null, null,
                    resolution == null ? "Solução removida durante a reabertura ou continuidade do atendimento." : resolution);
        }

        jdbcTemplate.update(
                "update support_ticket set severity = ?, status = ?, resolution = ?, "
                        + "resolved_at = case when ? in ('RESOLVED','CLOSED') and resolved_at is null then current_timestamp when ? not in ('RESOLVED','CLOSED') then null else resolved_at end, "
                        + "closed_at = case when ? = 'CLOSED' and closed_at is null then current_timestamp else closed_at end, "
                        + "updated_at = current_timestamp where id = ?",
                request.severity().name(), request.status().name(), resolution,
                request.status().name(), request.status().name(), request.status().name(), ticketId);
        auditService.record(principal.username(), "SUPPORT_TICKET_MANAGED", "SUPPORT_TICKET", Long.toString(ticketId),
                "Chamado atualizado para status " + request.status().name() + " e criticidade " + request.severity().name() + ".");
        return new TicketDetail(ticket(ticketId), events(ticketId));
    }

    public SupportSummary summary() {
        return jdbcTemplate.queryForObject(
                "select count(*) total, count(*) filter (where status = 'OPEN') open_count, count(*) filter (where status = 'IN_PROGRESS') in_progress_count, count(*) filter (where status = 'WAITING_REQUESTER') waiting_count, count(*) filter (where status = 'RESOLVED') resolved_count, count(*) filter (where status = 'CLOSED') closed_count, count(*) filter (where severity = 'CRITICAL' and status <> 'CLOSED') critical_active, count(*) filter (where severity = 'MEDIUM' and status <> 'CLOSED') medium_active, count(*) filter (where severity = 'LOW' and status <> 'CLOSED') low_active from support_ticket",
                (rs, rowNum) -> new SupportSummary(
                        rs.getLong("total"), rs.getLong("open_count"), rs.getLong("in_progress_count"), rs.getLong("waiting_count"), rs.getLong("resolved_count"), rs.getLong("closed_count"), rs.getLong("critical_active"), rs.getLong("medium_active"), rs.getLong("low_active")));
    }

    private List<TicketView> searchTickets(String ownershipClause, Long userId, String status, String severity, String search) {
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedStatus = status == null ? "" : status.trim();
        String normalizedSeverity = severity == null ? "" : severity.trim();
        String sql = TICKET_SELECT + " where " + ownershipClause
                + " and (? = '' or t.status = ?) and (? = '' or t.severity = ?)"
                + " and (? = '' or lower(t.subject) like lower(?) or lower(u.username) like lower(?) or lower(u.display_name) like lower(?))"
                + " order by case t.severity when 'CRITICAL' then 1 when 'MEDIUM' then 2 else 3 end, t.opened_at desc";
        Object[] params = userId == null
                ? new Object[]{normalizedStatus, normalizedStatus, normalizedSeverity, normalizedSeverity, normalizedSearch, "%" + normalizedSearch + "%", "%" + normalizedSearch + "%", "%" + normalizedSearch + "%"}
                : new Object[]{userId, normalizedStatus, normalizedStatus, normalizedSeverity, normalizedSeverity, normalizedSearch, "%" + normalizedSearch + "%", "%" + normalizedSearch + "%", "%" + normalizedSearch + "%"};
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapTicket(rs), params);
    }

    private TicketView ticket(long ticketId) {
        List<TicketView> tickets = jdbcTemplate.query(TICKET_SELECT + " where t.id = ?", (rs, rowNum) -> mapTicket(rs), ticketId);
        if (tickets.isEmpty()) throw new IllegalArgumentException("Chamado não encontrado.");
        return tickets.getFirst();
    }

    private TicketView mapTicket(java.sql.ResultSet rs) throws java.sql.SQLException {
        Severity severity = Severity.valueOf(rs.getString("severity"));
        return new TicketView(
                rs.getLong("id"), rs.getLong("opened_by_user_id"), rs.getString("requester_username"), rs.getString("requester_name"),
                rs.getString("subject"), rs.getString("description"), severity, TicketStatus.valueOf(rs.getString("status")), rs.getString("resolution"),
                rs.getObject("opened_at", OffsetDateTime.class), rs.getObject("first_support_response_at", OffsetDateTime.class), rs.getObject("resolved_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class), responseTargetHours(severity), solutionTargetHours(severity), false);
    }

    private List<TicketEventView> events(long ticketId) {
        return jdbcTemplate.query(
                "select id, actor_username, event_type, previous_value, new_value, message, created_at from support_ticket_event where ticket_id = ? order by created_at, id",
                (rs, rowNum) -> new TicketEventView(rs.getLong("id"), rs.getString("actor_username"), rs.getString("event_type"), rs.getString("previous_value"), rs.getString("new_value"), rs.getString("message"), rs.getObject("created_at", OffsetDateTime.class)),
                ticketId);
    }

    void requireTicketAccess(TicketView ticket, Authentication authentication) {
        KrinoUserPrincipal principal = principal(authentication);
        if (!ticket.openedByUserId().equals(principal.id()) && !authorizationService.hasNetworkPermission(authentication, "SUPPORT_TICKET_MANAGE")) {
            throw new AccessDeniedException("Você não possui acesso a este chamado.");
        }
    }

    void validateManagement(TicketStatus currentStatus, TicketStatus nextStatus, String resolution) {
        if (currentStatus == TicketStatus.CLOSED) {
            throw new IllegalArgumentException("Chamado encerrado é somente para consulta e não pode ser alterado.");
        }
        if ((nextStatus == TicketStatus.RESOLVED || nextStatus == TicketStatus.CLOSED) && resolution == null) {
            throw new IllegalArgumentException("Informe a solução antes de marcar o chamado como resolvido ou encerrado.");
        }
    }

    boolean shouldRegisterFirstSupportResponse(boolean manager, OffsetDateTime firstSupportResponseAt) {
        return manager && firstSupportResponseAt == null;
    }

    private KrinoUserPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) {
            throw new AccessDeniedException("Usuário autenticado não identificado.");
        }
        return principal;
    }

    private void addEvent(long ticketId, KrinoUserPrincipal actor, String eventType, String previousValue, String newValue, String message) {
        jdbcTemplate.update(
                "insert into support_ticket_event (ticket_id, actor_user_id, actor_username, event_type, previous_value, new_value, message) values (?, ?, ?, ?, ?, ?, ?)",
                ticketId, actor.id(), actor.username(), eventType, previousValue, newValue, message);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    int responseTargetHours(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 1;
            case MEDIUM -> 4;
            case LOW -> 24;
        };
    }

    int solutionTargetHours(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 4;
            case MEDIUM -> 24;
            case LOW -> 72;
        };
    }

    public enum Severity { CRITICAL, MEDIUM, LOW }
    public enum TicketStatus { OPEN, IN_PROGRESS, WAITING_REQUESTER, RESOLVED, CLOSED }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 4000) String description,
            @NotNull Severity severity) {}

    public record MessageRequest(@NotBlank @Size(max = 4000) String message) {}

    public record ManageTicketRequest(
            @NotNull Severity severity,
            @NotNull TicketStatus status,
            @Size(max = 4000) String resolution) {}

    public record TicketView(
            Long id,
            Long openedByUserId,
            String requesterUsername,
            String requesterName,
            String subject,
            String description,
            Severity severity,
            TicketStatus status,
            String resolution,
            OffsetDateTime openedAt,
            OffsetDateTime firstSupportResponseAt,
            OffsetDateTime resolvedAt,
            OffsetDateTime closedAt,
            OffsetDateTime updatedAt,
            int responseTargetHours,
            int solutionTargetHours,
            boolean contractualCountingRuleDefined) {}

    public record TicketEventView(Long id, String actorUsername, String eventType, String previousValue, String newValue, String message, OffsetDateTime createdAt) {}
    public record TicketDetail(TicketView ticket, List<TicketEventView> events) {}
    public record SupportSummary(long total, long open, long inProgress, long waitingRequester, long resolved, long closed, long criticalActive, long mediumActive, long lowActive) {}
}
