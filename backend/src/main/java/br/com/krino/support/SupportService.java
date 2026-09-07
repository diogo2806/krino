package br.com.krino.support;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.support.SlaClockCalculator.ClockPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Service
public class SupportService {

    private static final Set<String> CATEGORIES = Set.of("SUPPORT", "CORRECTIVE", "PREVENTIVE", "EVOLUTION");
    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "MEDIUM", "LOW");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "WAITING_REQUESTER", "RESOLVED", "CLOSED");
    private static final Set<String> COUNTING_RULES = Set.of("UNDEFINED", "ELAPSED", "BUSINESS");

    private final JdbcTemplate jdbcTemplate;
    private final SupportAccessService accessService;
    private final SlaClockCalculator clockCalculator;
    private final SecurityAuditService auditService;

    public SupportService(JdbcTemplate jdbcTemplate, SupportAccessService accessService,
            SlaClockCalculator clockCalculator, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.clockCalculator = clockCalculator;
        this.auditService = auditService;
    }

    public SupportContext context(Authentication authentication) {
        Set<Long> schoolIds = new LinkedHashSet<>(accessService.creatableSchoolIds(authentication));
        schoolIds.addAll(accessService.visibleManagementSchoolIds(authentication));
        List<SchoolOption> schools = schoolIds.isEmpty() ? List.of() : loadSchools(new ArrayList<>(schoolIds));
        List<AssessmentOption> assessments = schoolIds.isEmpty() ? List.of() : loadAssessments(new ArrayList<>(schoolIds));
        return new SupportContext(accessService.canManageAny(authentication), accessService.canReportAny(authentication),
                accessService.canManageSla(authentication), accessService.hasNetworkManagementVisibility(authentication),
                schools, assessments, slaPolicies());
    }

    public List<TicketView> list(String status, String severity, String search, Long schoolId, Authentication authentication) {
        long userId = accessService.currentUserId(authentication);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(ticketSelect()).append(" where 1=1");

        if (!accessService.hasNetworkManagementVisibility(authentication)) {
            List<Long> managedSchools = accessService.visibleManagementSchoolIds(authentication);
            sql.append(" and (t.created_by_user_id = ?");
            params.add(userId);
            if (!managedSchools.isEmpty()) {
                sql.append(" or t.school_id in (").append(placeholders(managedSchools.size())).append(")");
                params.addAll(managedSchools);
            }
            sql.append(")");
        }
        if (schoolId != null) {
            sql.append(" and t.school_id = ?");
            params.add(schoolId);
        }
        if (status != null && !status.isBlank()) {
            String normalized = normalizeStatus(status);
            sql.append(" and t.status = ?");
            params.add(normalized);
        }
        if (severity != null && !severity.isBlank()) {
            String normalized = normalizeSeverity(severity);
            sql.append(" and t.severity = ?");
            params.add(normalized);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" and (lower(t.protocol) like ? or lower(t.subject) like ? or lower(t.created_by_username) like ?)");
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(pattern); params.add(pattern); params.add(pattern);
        }
        sql.append(" order by case t.status when 'OPEN' then 1 when 'IN_PROGRESS' then 2 when 'WAITING_REQUESTER' then 3 when 'RESOLVED' then 4 else 5 end, "
                + "case t.severity when 'CRITICAL' then 1 when 'MEDIUM' then 2 else 3 end, t.opened_at desc");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapTicket(rs), params.toArray());
    }

    public TicketDetail get(long ticketId, Authentication authentication) {
        accessService.requireVisible(ticketId, authentication);
        TicketView ticket = loadTicket(ticketId);
        List<InteractionView> interactions = jdbcTemplate.query(
                "select id, interaction_type, actor_username, actor_role, message, created_at from support_ticket_interaction "
                        + "where ticket_id = ? order by created_at, id",
                (rs, rowNum) -> new InteractionView(rs.getLong("id"), rs.getString("interaction_type"),
                        rs.getString("actor_username"), rs.getString("actor_role"), rs.getString("message"),
                        rs.getObject("created_at", OffsetDateTime.class)), ticketId);
        return new TicketDetail(ticket, interactions, accessService.canManage(ticket.schoolId(), authentication));
    }

    @Transactional
    public TicketView create(@Valid TicketCreateRequest request, Authentication authentication) {
        String severity = normalizeSeverity(request.severity());
        String category = normalizeCategory(request.category());
        accessService.requireCreate(authentication, request.schoolId());
        if (request.assessmentId() != null) validateAssessment(request.assessmentId(), request.schoolId());

        SlaPolicyView policy = policy(severity);
        OffsetDateTime openedAt = OffsetDateTime.now(ZoneOffset.UTC);
        ClockPolicy clockPolicy = toClockPolicy(policy);
        OffsetDateTime responseDueAt = clockCalculator.dueAt(openedAt, policy.responseMinutes(), clockPolicy);
        OffsetDateTime solutionDueAt = clockCalculator.dueAt(openedAt, policy.solutionMinutes(), clockPolicy);
        long userId = accessService.currentUserId(authentication);

        Long id = jdbcTemplate.queryForObject(
                "insert into support_ticket (subject, description, category, severity, school_id, assessment_id, created_by_user_id, created_by_username, opened_at, "
                        + "sla_response_minutes, sla_solution_minutes, sla_counting_rule, sla_warning_minutes, sla_business_timezone, sla_workday_start, sla_workday_end, sla_business_days, response_due_at, solution_due_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, request.subject().trim(), request.description().trim(), category, severity, request.schoolId(), request.assessmentId(),
                userId, authentication.getName(), openedAt, policy.responseMinutes(), policy.solutionMinutes(), policy.countingRule(),
                policy.warningMinutes(), policy.businessTimezone(), policy.workdayStart(), policy.workdayEnd(), policy.businessDays(), responseDueAt, solutionDueAt);
        insertInteraction(id, "OPENED", userId, authentication.getName(), "REQUESTER",
                "Chamado aberto com criticidade " + severityLabel(severity) + " e tipo " + categoryLabel(category) + ".");
        auditService.record(authentication.getName(), "SUPPORT_TICKET_CREATE", "SUPPORT_TICKET", String.valueOf(id),
                "Criticidade=" + severity + ", tipo=" + category + (request.assessmentId() == null ? "" : ", avaliação=" + request.assessmentId()));
        return loadTicket(id);
    }

    @Transactional
    public InteractionView addInteraction(long ticketId, @Valid InteractionRequest request, Authentication authentication) {
        accessService.requireInteract(ticketId, authentication);
        TicketView ticket = loadTicket(ticketId);
        if ("CLOSED".equals(ticket.status())) throw new IllegalArgumentException("Chamados encerrados não recebem novas interações.");
        boolean supportActor = accessService.canManage(ticket.schoolId(), authentication);
        long userId = accessService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (supportActor && ticket.firstResponseAt() == null) {
            jdbcTemplate.update("update support_ticket set first_response_at = ?, updated_at = current_timestamp where id = ? and first_response_at is null", now, ticketId);
        }
        Long interactionId = insertInteraction(ticketId, "MESSAGE", userId, authentication.getName(), supportActor ? "SUPPORT" : "REQUESTER", request.message().trim());
        auditService.record(authentication.getName(), "SUPPORT_TICKET_MESSAGE", "SUPPORT_TICKET", String.valueOf(ticketId),
                "Interação registrada por " + (supportActor ? "atendimento" : "solicitante"));
        return loadInteraction(interactionId);
    }

    @Transactional
    public TicketView update(long ticketId, @Valid TicketUpdateRequest request, Authentication authentication) {
        accessService.requireManage(ticketId, authentication);
        TicketView current = loadTicket(ticketId);
        if ("CLOSED".equals(current.status())) throw new IllegalArgumentException("Chamados encerrados não podem ser alterados.");
        if (request.status() == null && request.severity() == null && request.resolution() == null) {
            throw new IllegalArgumentException("Informe ao menos uma alteração para o chamado.");
        }

        String nextStatus = request.status() == null ? current.status() : normalizeStatus(request.status());
        String nextSeverity = request.severity() == null ? current.severity() : normalizeSeverity(request.severity());
        validateTransition(current.status(), nextStatus);
        if (!nextSeverity.equals(current.severity()) && "RESOLVED".equals(current.status())) {
            throw new IllegalArgumentException("Reabra o chamado antes de alterar a criticidade de um atendimento resolvido.");
        }

        String nextResolution = request.resolution() == null ? current.resolution() : blankToNull(request.resolution());
        boolean reopening = "RESOLVED".equals(current.status()) && "IN_PROGRESS".equals(nextStatus);
        if (reopening) nextResolution = null;
        if (("RESOLVED".equals(nextStatus) || "CLOSED".equals(nextStatus)) && (nextResolution == null || nextResolution.isBlank())) {
            throw new IllegalArgumentException("Registre a solução antes de resolver ou encerrar o chamado.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime firstResponseAt = current.firstResponseAt() == null ? now : current.firstResponseAt();
        OffsetDateTime resolvedAt = current.resolvedAt();
        OffsetDateTime closedAt = current.closedAt();
        if ("RESOLVED".equals(nextStatus) && !"RESOLVED".equals(current.status())) resolvedAt = now;
        if (reopening) resolvedAt = null;
        if ("CLOSED".equals(nextStatus) && !"CLOSED".equals(current.status())) {
            if (resolvedAt == null) resolvedAt = now;
            closedAt = now;
        }

        SlaSnapshot snapshot = snapshotFrom(current);
        if (!nextSeverity.equals(current.severity())) {
            SlaPolicyView policy = policy(nextSeverity);
            ClockPolicy clockPolicy = toClockPolicy(policy);
            OffsetDateTime responseDue = current.firstResponseAt() == null
                    ? clockCalculator.dueAt(current.openedAt(), policy.responseMinutes(), clockPolicy)
                    : current.responseDueAt();
            OffsetDateTime solutionDue = current.resolvedAt() == null
                    ? clockCalculator.dueAt(current.openedAt(), policy.solutionMinutes(), clockPolicy)
                    : current.solutionDueAt();
            snapshot = new SlaSnapshot(policy.responseMinutes(), policy.solutionMinutes(), policy.countingRule(), policy.warningMinutes(),
                    policy.businessTimezone(), policy.workdayStart(), policy.workdayEnd(), policy.businessDays(), responseDue, solutionDue);
        }

        jdbcTemplate.update(
                "update support_ticket set severity = ?, status = ?, resolution = ?, first_response_at = ?, resolved_at = ?, closed_at = ?, "
                        + "sla_response_minutes = ?, sla_solution_minutes = ?, sla_counting_rule = ?, sla_warning_minutes = ?, sla_business_timezone = ?, "
                        + "sla_workday_start = ?, sla_workday_end = ?, sla_business_days = ?, response_due_at = ?, solution_due_at = ?, updated_at = current_timestamp where id = ?",
                nextSeverity, nextStatus, nextResolution, firstResponseAt, resolvedAt, closedAt,
                snapshot.responseMinutes(), snapshot.solutionMinutes(), snapshot.countingRule(), snapshot.warningMinutes(), snapshot.businessTimezone(),
                snapshot.workdayStart(), snapshot.workdayEnd(), snapshot.businessDays(), snapshot.responseDueAt(), snapshot.solutionDueAt(), ticketId);

        long actorId = accessService.currentUserId(authentication);
        if (!nextSeverity.equals(current.severity())) {
            insertInteraction(ticketId, "SEVERITY_CHANGE", actorId, authentication.getName(), "SUPPORT",
                    "Criticidade alterada de " + severityLabel(current.severity()) + " para " + severityLabel(nextSeverity) + ". Os prazos futuros foram recalculados a partir da abertura conforme a política vigente da nova criticidade.");
        }
        if (!nextStatus.equals(current.status())) {
            insertInteraction(ticketId, "STATUS_CHANGE", actorId, authentication.getName(), "SUPPORT",
                    "Status alterado de " + statusLabel(current.status()) + " para " + statusLabel(nextStatus) + ".");
        }
        if (nextResolution != null && !nextResolution.equals(current.resolution())) {
            insertInteraction(ticketId, "SOLUTION", actorId, authentication.getName(), "SUPPORT", "Solução registrada: " + nextResolution);
        }
        auditService.record(authentication.getName(), "SUPPORT_TICKET_UPDATE", "SUPPORT_TICKET", String.valueOf(ticketId),
                "Status=" + nextStatus + ", criticidade=" + nextSeverity);
        return loadTicket(ticketId);
    }

    public List<SlaPolicyView> slaPolicies() {
        return jdbcTemplate.query(
                "select severity, response_minutes, solution_minutes, counting_rule, warning_minutes, business_timezone, workday_start, workday_end, business_days, updated_by, updated_at "
                        + "from support_sla_policy order by case severity when 'CRITICAL' then 1 when 'MEDIUM' then 2 else 3 end",
                (rs, rowNum) -> new SlaPolicyView(rs.getString("severity"), rs.getInt("response_minutes"), rs.getInt("solution_minutes"),
                        rs.getString("counting_rule"), (Integer) rs.getObject("warning_minutes"), rs.getString("business_timezone"),
                        rs.getObject("workday_start", LocalTime.class), rs.getObject("workday_end", LocalTime.class), rs.getString("business_days"),
                        rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class)));
    }

    @Transactional
    public SlaPolicyView updatePolicy(String severityValue, @Valid SlaPolicyRequest request, Authentication authentication) {
        accessService.requireSlaManage(authentication);
        String severity = normalizeSeverity(severityValue);
        String rule = normalizeCountingRule(request.countingRule());
        String timezone = blankToNull(request.businessTimezone());
        String businessDays = normalizeBusinessDays(request.businessDays());
        LocalTime workdayStart = request.workdayStart();
        LocalTime workdayEnd = request.workdayEnd();
        Integer warningMinutes = request.warningMinutes();

        if ("BUSINESS".equals(rule)) {
            if (timezone == null || workdayStart == null || workdayEnd == null || businessDays == null) {
                throw new IllegalArgumentException("Para horas úteis, informe fuso horário, dias úteis e início/fim da jornada.");
            }
            clockCalculator.dueAt(OffsetDateTime.now(ZoneOffset.UTC), 1,
                    new ClockPolicy(rule, timezone, workdayStart, workdayEnd, businessDays));
        }
        if (!"BUSINESS".equals(rule)) {
            timezone = null; businessDays = null; workdayStart = null; workdayEnd = null;
        }
        int updated = jdbcTemplate.update(
                "update support_sla_policy set response_minutes = ?, solution_minutes = ?, counting_rule = ?, warning_minutes = ?, business_timezone = ?, "
                        + "workday_start = ?, workday_end = ?, business_days = ?, updated_by = ?, updated_at = current_timestamp where severity = ?",
                request.responseMinutes(), request.solutionMinutes(), rule, warningMinutes, timezone, workdayStart, workdayEnd, businessDays,
                authentication.getName(), severity);
        if (updated == 0) throw new IllegalArgumentException("Política de criticidade não encontrada.");
        auditService.record(authentication.getName(), "SUPPORT_SLA_POLICY_UPDATE", "SUPPORT_SLA_POLICY", severity,
                "Contagem=" + rule + ", resposta=" + request.responseMinutes() + "min, solução=" + request.solutionMinutes() + "min");
        return policy(severity);
    }

    public SupportReport report(Long schoolId, Authentication authentication) {
        accessService.requireReport(authentication, schoolId);
        String scope = schoolId == null ? "" : " where school_id = ?";
        Object[] params = schoolId == null ? new Object[0] : new Object[] { schoolId };
        ReportTotals totals = jdbcTemplate.queryForObject(
                "select count(*) total, "
                        + "count(*) filter (where status in ('OPEN','IN_PROGRESS','WAITING_REQUESTER')) active, "
                        + "count(*) filter (where status = 'RESOLVED') resolved, count(*) filter (where status = 'CLOSED') closed, "
                        + "count(*) filter (where response_due_at is not null and ((first_response_at is null and current_timestamp > response_due_at) or first_response_at > response_due_at)) response_breaches, "
                        + "count(*) filter (where solution_due_at is not null and (((resolved_at is null and closed_at is null) and current_timestamp > solution_due_at) or coalesce(resolved_at, closed_at) > solution_due_at)) solution_breaches, "
                        + "avg(extract(epoch from (first_response_at - opened_at))/60) filter (where first_response_at is not null) avg_response_minutes, "
                        + "avg(extract(epoch from (coalesce(resolved_at, closed_at) - opened_at))/60) filter (where coalesce(resolved_at, closed_at) is not null) avg_solution_minutes "
                        + "from support_ticket" + scope,
                (rs, rowNum) -> new ReportTotals(rs.getLong("total"), rs.getLong("active"), rs.getLong("resolved"), rs.getLong("closed"),
                        rs.getLong("response_breaches"), rs.getLong("solution_breaches"), rs.getDouble("avg_response_minutes"),
                        rs.wasNull() ? null : rs.getDouble("avg_solution_minutes")), params);
        List<SeverityReport> bySeverity = jdbcTemplate.query(
                "select severity, count(*) total, count(*) filter (where status in ('RESOLVED','CLOSED')) completed, "
                        + "count(*) filter (where response_due_at is not null and ((first_response_at is null and current_timestamp > response_due_at) or first_response_at > response_due_at)) response_breaches, "
                        + "count(*) filter (where solution_due_at is not null and (((resolved_at is null and closed_at is null) and current_timestamp > solution_due_at) or coalesce(resolved_at, closed_at) > solution_due_at)) solution_breaches "
                        + "from support_ticket" + scope + " group by severity order by case severity when 'CRITICAL' then 1 when 'MEDIUM' then 2 else 3 end",
                (rs, rowNum) -> new SeverityReport(rs.getString("severity"), rs.getLong("total"), rs.getLong("completed"),
                        rs.getLong("response_breaches"), rs.getLong("solution_breaches")), params);
        return new SupportReport(totals == null ? new ReportTotals(0,0,0,0,0,0,null,null) : totals, bySeverity);
    }

    private List<SchoolOption> loadSchools(List<Long> ids) {
        String in = placeholders(ids.size());
        return jdbcTemplate.query("select id, name from school_unit where active = true and id in (" + in + ") order by name",
                (rs, rowNum) -> new SchoolOption(rs.getLong("id"), rs.getString("name")), ids.toArray());
    }

    private List<AssessmentOption> loadAssessments(List<Long> schoolIds) {
        String in = placeholders(schoolIds.size());
        return jdbcTemplate.query(
                "select distinct a.id, a.name, s.school_id from network_assessment a join network_assessment_scope s on s.assessment_id = a.id "
                        + "where s.school_id in (" + in + ") and a.status <> 'CLOSED' order by a.name, s.school_id",
                (rs, rowNum) -> new AssessmentOption(rs.getLong("id"), rs.getString("name"), rs.getLong("school_id")), schoolIds.toArray());
    }

    private void validateAssessment(long assessmentId, Long schoolId) {
        if (schoolId == null) throw new IllegalArgumentException("Selecione a unidade escolar para vincular uma Avaliação em Rede ao chamado.");
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from network_assessment_scope where assessment_id = ? and school_id = ?", Integer.class, assessmentId, schoolId);
        if (count == null || count == 0) throw new IllegalArgumentException("A avaliação informada não pertence à unidade escolar selecionada.");
    }

    private TicketView loadTicket(long ticketId) {
        List<TicketView> rows = jdbcTemplate.query(ticketSelect() + " where t.id = ?", (rs, rowNum) -> mapTicket(rs), ticketId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Chamado não encontrado.");
        return rows.getFirst();
    }

    private TicketView mapTicket(java.sql.ResultSet rs) throws java.sql.SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime responseDue = rs.getObject("response_due_at", OffsetDateTime.class);
        OffsetDateTime solutionDue = rs.getObject("solution_due_at", OffsetDateTime.class);
        OffsetDateTime firstResponse = rs.getObject("first_response_at", OffsetDateTime.class);
        OffsetDateTime resolved = rs.getObject("resolved_at", OffsetDateTime.class);
        OffsetDateTime closed = rs.getObject("closed_at", OffsetDateTime.class);
        Integer warning = (Integer) rs.getObject("sla_warning_minutes");
        return new TicketView(rs.getLong("id"), rs.getString("protocol"), rs.getString("subject"), rs.getString("description"),
                rs.getString("category"), rs.getString("severity"), rs.getString("status"), (Long) rs.getObject("school_id"),
                rs.getString("school_name"), (Long) rs.getObject("assessment_id"), rs.getString("assessment_name"),
                rs.getLong("created_by_user_id"), rs.getString("created_by_username"), rs.getString("resolution"),
                rs.getObject("opened_at", OffsetDateTime.class), firstResponse, resolved, closed,
                rs.getInt("sla_response_minutes"), rs.getInt("sla_solution_minutes"), rs.getString("sla_counting_rule"), warning,
                responseDue, solutionDue, deadlineState(now, responseDue, firstResponse, warning),
                deadlineState(now, solutionDue, resolved != null ? resolved : closed, warning));
    }

    private String ticketSelect() {
        return "select t.id, t.protocol, t.subject, t.description, t.category, t.severity, t.status, t.school_id, s.name school_name, "
                + "t.assessment_id, a.name assessment_name, t.created_by_user_id, t.created_by_username, t.resolution, t.opened_at, t.first_response_at, "
                + "t.resolved_at, t.closed_at, t.sla_response_minutes, t.sla_solution_minutes, t.sla_counting_rule, t.sla_warning_minutes, "
                + "t.response_due_at, t.solution_due_at from support_ticket t left join school_unit s on s.id = t.school_id "
                + "left join network_assessment a on a.id = t.assessment_id";
    }

    private Long insertInteraction(long ticketId, String type, Long actorUserId, String actorUsername, String actorRole, String message) {
        return jdbcTemplate.queryForObject(
                "insert into support_ticket_interaction (ticket_id, interaction_type, actor_user_id, actor_username, actor_role, message) values (?, ?, ?, ?, ?, ?) returning id",
                Long.class, ticketId, type, actorUserId, actorUsername == null || actorUsername.isBlank() ? "SYSTEM" : actorUsername, actorRole, message);
    }

    private InteractionView loadInteraction(long interactionId) {
        return jdbcTemplate.query(
                "select id, interaction_type, actor_username, actor_role, message, created_at from support_ticket_interaction where id = ?",
                (rs, rowNum) -> new InteractionView(rs.getLong("id"), rs.getString("interaction_type"), rs.getString("actor_username"),
                        rs.getString("actor_role"), rs.getString("message"), rs.getObject("created_at", OffsetDateTime.class)), interactionId).getFirst();
    }

    private SlaPolicyView policy(String severity) {
        List<SlaPolicyView> rows = jdbcTemplate.query(
                "select severity, response_minutes, solution_minutes, counting_rule, warning_minutes, business_timezone, workday_start, workday_end, business_days, updated_by, updated_at "
                        + "from support_sla_policy where severity = ?",
                (rs, rowNum) -> new SlaPolicyView(rs.getString("severity"), rs.getInt("response_minutes"), rs.getInt("solution_minutes"),
                        rs.getString("counting_rule"), (Integer) rs.getObject("warning_minutes"), rs.getString("business_timezone"),
                        rs.getObject("workday_start", LocalTime.class), rs.getObject("workday_end", LocalTime.class), rs.getString("business_days"),
                        rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class)), severity);
        if (rows.isEmpty()) throw new IllegalArgumentException("Política de SLA não encontrada para a criticidade.");
        return rows.getFirst();
    }

    private ClockPolicy toClockPolicy(SlaPolicyView policy) {
        return new ClockPolicy(policy.countingRule(), policy.businessTimezone(), policy.workdayStart(), policy.workdayEnd(), policy.businessDays());
    }

    private SlaSnapshot snapshotFrom(TicketView ticket) {
        return jdbcTemplate.query(
                "select sla_response_minutes, sla_solution_minutes, sla_counting_rule, sla_warning_minutes, sla_business_timezone, sla_workday_start, sla_workday_end, sla_business_days, response_due_at, solution_due_at "
                        + "from support_ticket where id = ?",
                (rs, rowNum) -> new SlaSnapshot(rs.getInt("sla_response_minutes"), rs.getInt("sla_solution_minutes"), rs.getString("sla_counting_rule"),
                        (Integer) rs.getObject("sla_warning_minutes"), rs.getString("sla_business_timezone"), rs.getObject("sla_workday_start", LocalTime.class),
                        rs.getObject("sla_workday_end", LocalTime.class), rs.getString("sla_business_days"),
                        rs.getObject("response_due_at", OffsetDateTime.class), rs.getObject("solution_due_at", OffsetDateTime.class)), ticket.id()).getFirst();
    }

    private String deadlineState(OffsetDateTime now, OffsetDateTime dueAt, OffsetDateTime completedAt, Integer warningMinutes) {
        if (dueAt == null) return "NOT_CONFIGURED";
        if (completedAt != null) return completedAt.isAfter(dueAt) ? "BREACHED" : "MET";
        if (now.isAfter(dueAt)) return "BREACHED";
        if (warningMinutes != null && !now.isBefore(dueAt.minusMinutes(warningMinutes))) return "AT_RISK";
        return "ON_TIME";
    }

    private void validateTransition(String current, String next) {
        if (current.equals(next)) return;
        boolean allowed = switch (current) {
            case "OPEN" -> Set.of("IN_PROGRESS", "WAITING_REQUESTER", "RESOLVED", "CLOSED").contains(next);
            case "IN_PROGRESS" -> Set.of("WAITING_REQUESTER", "RESOLVED", "CLOSED").contains(next);
            case "WAITING_REQUESTER" -> Set.of("IN_PROGRESS", "RESOLVED", "CLOSED").contains(next);
            case "RESOLVED" -> Set.of("IN_PROGRESS", "CLOSED").contains(next);
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("Transição de status inválida para o chamado.");
    }

    private String normalizeCategory(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(normalized)) throw new IllegalArgumentException("Tipo de atendimento inválido.");
        return normalized;
    }

    private String normalizeSeverity(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!SEVERITIES.contains(normalized)) throw new IllegalArgumentException("Criticidade inválida.");
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("Status de chamado inválido.");
        return normalized;
    }

    private String normalizeCountingRule(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!COUNTING_RULES.contains(normalized)) throw new IllegalArgumentException("Regra de contagem do SLA inválida.");
        return normalized;
    }

    private String normalizeBusinessDays(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            Set<DayOfWeek> values = new LinkedHashSet<>();
            Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank())
                    .map(item -> DayOfWeek.valueOf(item.toUpperCase(Locale.ROOT))).forEach(values::add);
            if (values.isEmpty()) return null;
            List<DayOfWeek> ordered = new ArrayList<>(values);
            Collections.sort(ordered);
            return ordered.stream().map(Enum::name).reduce((left, right) -> left + "," + right).orElse(null);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Dias úteis inválidos para a regra de SLA.");
        }
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String severityLabel(String severity) {
        return switch (severity) { case "CRITICAL" -> "Crítico"; case "MEDIUM" -> "Médio"; default -> "Baixo"; };
    }

    private String categoryLabel(String category) {
        return switch (category) { case "CORRECTIVE" -> "Manutenção corretiva"; case "PREVENTIVE" -> "Manutenção preventiva"; case "EVOLUTION" -> "Evolução"; default -> "Suporte"; };
    }

    private String statusLabel(String status) {
        return switch (status) { case "IN_PROGRESS" -> "Em atendimento"; case "WAITING_REQUESTER" -> "Aguardando solicitante"; case "RESOLVED" -> "Resolvido"; case "CLOSED" -> "Encerrado"; default -> "Aberto"; };
    }

    public record SupportContext(boolean canManage, boolean canReport, boolean canManageSla, boolean networkManagementView,
            List<SchoolOption> schools, List<AssessmentOption> assessments, List<SlaPolicyView> slaPolicies) {}
    public record SchoolOption(long id, String name) {}
    public record AssessmentOption(long id, String name, long schoolId) {}

    public record TicketCreateRequest(
            @NotBlank(message = "Informe o assunto do chamado.") @Size(max = 180) String subject,
            @NotBlank(message = "Descreva a solicitação.") @Size(max = 4000) String description,
            @NotBlank(message = "Informe o tipo de atendimento.") String category,
            @NotBlank(message = "Informe a criticidade.") String severity,
            Long schoolId, Long assessmentId) {}

    public record InteractionRequest(@NotBlank(message = "Escreva a mensagem antes de enviar.") @Size(max = 4000) String message) {}

    public record TicketUpdateRequest(String status, String severity, @Size(max = 4000) String resolution) {}

    public record SlaPolicyRequest(
            @NotNull @Min(value = 1, message = "O prazo de resposta deve ser maior que zero.") Integer responseMinutes,
            @NotNull @Min(value = 1, message = "O prazo de solução deve ser maior que zero.") Integer solutionMinutes,
            @NotBlank(message = "Informe a regra de contagem.") String countingRule,
            @Min(value = 1, message = "O aviso de risco deve ser maior que zero.") Integer warningMinutes,
            String businessTimezone, LocalTime workdayStart, LocalTime workdayEnd, String businessDays) {}

    public record TicketView(long id, String protocol, String subject, String description, String category, String severity, String status,
            Long schoolId, String schoolName, Long assessmentId, String assessmentName, long createdByUserId, String createdByUsername,
            String resolution, OffsetDateTime openedAt, OffsetDateTime firstResponseAt, OffsetDateTime resolvedAt, OffsetDateTime closedAt,
            int responseMinutes, int solutionMinutes, String countingRule, Integer warningMinutes, OffsetDateTime responseDueAt,
            OffsetDateTime solutionDueAt, String responseSlaState, String solutionSlaState) {}

    public record TicketDetail(TicketView ticket, List<InteractionView> interactions, boolean canManage) {}
    public record InteractionView(long id, String interactionType, String actorUsername, String actorRole, String message, OffsetDateTime createdAt) {}
    public record SlaPolicyView(String severity, int responseMinutes, int solutionMinutes, String countingRule, Integer warningMinutes,
            String businessTimezone, LocalTime workdayStart, LocalTime workdayEnd, String businessDays, String updatedBy, OffsetDateTime updatedAt) {}
    public record SupportReport(ReportTotals totals, List<SeverityReport> bySeverity) {}
    public record ReportTotals(long total, long active, long resolved, long closed, long responseBreaches, long solutionBreaches,
            Double averageResponseMinutes, Double averageSolutionMinutes) {}
    public record SeverityReport(String severity, long total, long completed, long responseBreaches, long solutionBreaches) {}

    private record SlaSnapshot(int responseMinutes, int solutionMinutes, String countingRule, Integer warningMinutes,
            String businessTimezone, LocalTime workdayStart, LocalTime workdayEnd, String businessDays,
            OffsetDateTime responseDueAt, OffsetDateTime solutionDueAt) {}
}
