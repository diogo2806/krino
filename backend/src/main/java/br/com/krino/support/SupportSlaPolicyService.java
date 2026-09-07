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
import br.com.krino.support.SupportService.SlaPolicyView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Service
public class SupportSlaPolicyService {

    private static final Set<String> COUNTING_RULES = Set.of("UNDEFINED", "ELAPSED", "BUSINESS");

    private final JdbcTemplate jdbcTemplate;
    private final SupportAccessService accessService;
    private final SlaClockCalculator clockCalculator;
    private final SecurityAuditService auditService;

    public SupportSlaPolicyService(JdbcTemplate jdbcTemplate, SupportAccessService accessService,
            SlaClockCalculator clockCalculator, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.clockCalculator = clockCalculator;
        this.auditService = auditService;
    }

    @Transactional
    public SlaPolicyView update(String severityValue, @Valid SlaCountingRuleRequest request, Authentication authentication) {
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
        } else {
            timezone = null;
            businessDays = null;
            workdayStart = null;
            workdayEnd = null;
        }

        int updated = jdbcTemplate.update(
                "update support_sla_policy set counting_rule = ?, warning_minutes = ?, business_timezone = ?, workday_start = ?, workday_end = ?, "
                        + "business_days = ?, updated_by = ?, updated_at = current_timestamp where severity = ?",
                rule, warningMinutes, timezone, workdayStart, workdayEnd, businessDays, authentication.getName(), severity);
        if (updated == 0) throw new IllegalArgumentException("Política de criticidade não encontrada.");

        auditService.record(authentication.getName(), "SUPPORT_SLA_POLICY_UPDATE", "SUPPORT_SLA_POLICY", severity,
                "Contagem=" + rule + ", prazos contratuais preservados");
        return load(severity);
    }

    private SlaPolicyView load(String severity) {
        return jdbcTemplate.query(
                "select severity, response_minutes, solution_minutes, counting_rule, warning_minutes, business_timezone, workday_start, workday_end, business_days, updated_by, updated_at "
                        + "from support_sla_policy where severity = ?",
                (rs, rowNum) -> new SlaPolicyView(rs.getString("severity"), rs.getInt("response_minutes"), rs.getInt("solution_minutes"),
                        rs.getString("counting_rule"), (Integer) rs.getObject("warning_minutes"), rs.getString("business_timezone"),
                        rs.getObject("workday_start", LocalTime.class), rs.getObject("workday_end", LocalTime.class), rs.getString("business_days"),
                        rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class)), severity).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Política de criticidade não encontrada."));
    }

    private String normalizeSeverity(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CRITICAL", "MEDIUM", "LOW").contains(normalized)) throw new IllegalArgumentException("Criticidade inválida.");
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SlaCountingRuleRequest(
            @NotBlank(message = "Informe a regra de contagem.") String countingRule,
            @Min(value = 1, message = "O aviso de risco deve ser maior que zero.") Integer warningMinutes,
            String businessTimezone, LocalTime workdayStart, LocalTime workdayEnd, String businessDays) {}
}
