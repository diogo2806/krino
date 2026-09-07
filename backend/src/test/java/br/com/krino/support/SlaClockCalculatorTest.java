package br.com.krino.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import br.com.krino.support.SlaClockCalculator.ClockPolicy;

class SlaClockCalculatorTest {

    private final SlaClockCalculator calculator = new SlaClockCalculator();

    @Test
    void shouldNotInventDueDateWhenCountingRuleIsUndefined() {
        OffsetDateTime openedAt = OffsetDateTime.of(2026, 9, 7, 12, 0, 0, 0, ZoneOffset.UTC);

        OffsetDateTime dueAt = calculator.dueAt(openedAt, 60,
                new ClockPolicy("UNDEFINED", null, null, null, null));

        assertThat(dueAt).isNull();
    }

    @Test
    void shouldCalculateElapsedMinutesFromOpening() {
        OffsetDateTime openedAt = OffsetDateTime.of(2026, 9, 7, 12, 0, 0, 0, ZoneOffset.UTC);

        OffsetDateTime dueAt = calculator.dueAt(openedAt, 240,
                new ClockPolicy("ELAPSED", null, null, null, null));

        assertThat(dueAt).isEqualTo(OffsetDateTime.of(2026, 9, 7, 16, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void shouldCarryBusinessMinutesToNextConfiguredWorkday() {
        OffsetDateTime openedAt = OffsetDateTime.of(2026, 9, 7, 19, 30, 0, 0, ZoneOffset.UTC);
        ClockPolicy policy = new ClockPolicy("BUSINESS", "America/Sao_Paulo",
                LocalTime.of(8, 0), LocalTime.of(17, 0), "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY");

        OffsetDateTime dueAt = calculator.dueAt(openedAt, 120, policy);

        assertThat(dueAt.toInstant()).isEqualTo(OffsetDateTime.of(2026, 9, 8, 12, 30, 0, 0, ZoneOffset.UTC).toInstant());
    }
}
