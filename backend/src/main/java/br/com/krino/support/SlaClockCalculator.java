package br.com.krino.support;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class SlaClockCalculator {

    public OffsetDateTime dueAt(OffsetDateTime openedAt, int minutes, ClockPolicy policy) {
        if (openedAt == null) throw new IllegalArgumentException("Data de abertura não informada.");
        if (minutes <= 0) throw new IllegalArgumentException("O prazo deve ser maior que zero.");
        String rule = policy == null || policy.countingRule() == null ? "UNDEFINED" : policy.countingRule().trim().toUpperCase(Locale.ROOT);
        return switch (rule) {
            case "UNDEFINED" -> null;
            case "ELAPSED" -> openedAt.plusMinutes(minutes);
            case "BUSINESS" -> addBusinessMinutes(openedAt, minutes, policy);
            default -> throw new IllegalArgumentException("Regra de contagem do SLA inválida.");
        };
    }

    private OffsetDateTime addBusinessMinutes(OffsetDateTime openedAt, int minutes, ClockPolicy policy) {
        ZoneId zone = requireZone(policy.businessTimezone());
        LocalTime workdayStart = policy.workdayStart();
        LocalTime workdayEnd = policy.workdayEnd();
        if (workdayStart == null || workdayEnd == null || !workdayEnd.isAfter(workdayStart)) {
            throw new IllegalArgumentException("Informe uma jornada útil válida para calcular o SLA.");
        }
        Set<DayOfWeek> businessDays = parseBusinessDays(policy.businessDays());
        ZonedDateTime current = alignToBusinessTime(openedAt.toInstant().atZone(zone), businessDays, workdayStart, workdayEnd, zone);
        long remaining = minutes;
        while (remaining > 0) {
            ZonedDateTime workdayEndAt = current.toLocalDate().atTime(workdayEnd).atZone(zone);
            long available = Math.max(0, Duration.between(current, workdayEndAt).toMinutes());
            if (remaining <= available) return current.plusMinutes(remaining).toOffsetDateTime();
            remaining -= available;
            current = nextBusinessStart(current.toLocalDate().plusDays(1), businessDays, workdayStart, zone);
        }
        return current.toOffsetDateTime();
    }

    private ZonedDateTime alignToBusinessTime(ZonedDateTime instant, Set<DayOfWeek> businessDays,
            LocalTime workdayStart, LocalTime workdayEnd, ZoneId zone) {
        LocalDate date = instant.toLocalDate();
        if (!businessDays.contains(date.getDayOfWeek())) return nextBusinessStart(date.plusDays(1), businessDays, workdayStart, zone);
        LocalTime time = instant.toLocalTime();
        if (time.isBefore(workdayStart)) return date.atTime(workdayStart).atZone(zone);
        if (!time.isBefore(workdayEnd)) return nextBusinessStart(date.plusDays(1), businessDays, workdayStart, zone);
        return instant;
    }

    private ZonedDateTime nextBusinessStart(LocalDate date, Set<DayOfWeek> businessDays, LocalTime workdayStart, ZoneId zone) {
        LocalDate current = date;
        for (int attempts = 0; attempts < 14; attempts++) {
            if (businessDays.contains(current.getDayOfWeek())) return current.atTime(workdayStart).atZone(zone);
            current = current.plusDays(1);
        }
        throw new IllegalArgumentException("Não foi possível localizar um próximo dia útil para o SLA.");
    }

    private ZoneId requireZone(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Informe o fuso horário da jornada útil.");
        try {
            return ZoneId.of(value.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Fuso horário inválido para o SLA.");
        }
    }

    private Set<DayOfWeek> parseBusinessDays(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Informe os dias úteis usados na contagem do SLA.");
        try {
            Set<DayOfWeek> days = Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .map(item -> DayOfWeek.valueOf(item.toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toSet());
            if (days.isEmpty()) throw new IllegalArgumentException();
            return days;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Dias úteis inválidos para o SLA.");
        }
    }

    public record ClockPolicy(String countingRule, String businessTimezone, LocalTime workdayStart,
            LocalTime workdayEnd, String businessDays) {}
}
