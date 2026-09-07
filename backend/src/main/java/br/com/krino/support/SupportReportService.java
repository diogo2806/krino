package br.com.krino.support;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SupportReportService {

    private final JdbcTemplate jdbcTemplate;
    private final SupportAccessService accessService;

    public SupportReportService(JdbcTemplate jdbcTemplate, SupportAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
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
                (rs, rowNum) -> new ReportTotals(
                        rs.getLong("total"), rs.getLong("active"), rs.getLong("resolved"), rs.getLong("closed"),
                        rs.getLong("response_breaches"), rs.getLong("solution_breaches"),
                        nullableDouble(rs.getBigDecimal("avg_response_minutes")),
                        nullableDouble(rs.getBigDecimal("avg_solution_minutes"))),
                params);

        List<SeverityReport> bySeverity = jdbcTemplate.query(
                "select severity, count(*) total, count(*) filter (where status in ('RESOLVED','CLOSED')) completed, "
                        + "count(*) filter (where response_due_at is not null and ((first_response_at is null and current_timestamp > response_due_at) or first_response_at > response_due_at)) response_breaches, "
                        + "count(*) filter (where solution_due_at is not null and (((resolved_at is null and closed_at is null) and current_timestamp > solution_due_at) or coalesce(resolved_at, closed_at) > solution_due_at)) solution_breaches "
                        + "from support_ticket" + scope
                        + " group by severity order by case severity when 'CRITICAL' then 1 when 'MEDIUM' then 2 else 3 end",
                (rs, rowNum) -> new SeverityReport(
                        rs.getString("severity"), rs.getLong("total"), rs.getLong("completed"),
                        rs.getLong("response_breaches"), rs.getLong("solution_breaches")),
                params);

        return new SupportReport(totals == null ? new ReportTotals(0, 0, 0, 0, 0, 0, null, null) : totals, bySeverity);
    }

    private Double nullableDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    public record SupportReport(ReportTotals totals, List<SeverityReport> bySeverity) {}
    public record ReportTotals(long total, long active, long resolved, long closed, long responseBreaches, long solutionBreaches,
            Double averageResponseMinutes, Double averageSolutionMinutes) {}
    public record SeverityReport(String severity, long total, long completed, long responseBreaches, long solutionBreaches) {}
}
