package br.com.krino.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AssessmentReportServiceTest {

    @Test
    void shouldCalculateSeventyFivePercentWithTwoDecimalPlaces() throws Exception {
        AssessmentReportService service = new AssessmentReportService(null, null);
        Method percentage = AssessmentReportService.class.getDeclaredMethod("percentage", long.class, long.class);
        percentage.setAccessible(true);

        BigDecimal result = (BigDecimal) percentage.invoke(service, 150L, 200L);

        assertThat(result).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void shouldReturnNullWhenPercentageBaseIsZero() throws Exception {
        AssessmentReportService service = new AssessmentReportService(null, null);
        Method percentage = AssessmentReportService.class.getDeclaredMethod("percentage", long.class, long.class);
        percentage.setAccessible(true);

        BigDecimal result = (BigDecimal) percentage.invoke(service, 10L, 0L);

        assertThat(result).isNull();
    }
}
