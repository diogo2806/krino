package br.com.krino.monitoring;

import java.math.BigDecimal;

public interface PedagogicalMetricProvider {

    String sourceCode();
    String sourceLabel();
    SourceMetric load(MetricFilter filter);

    record MetricFilter(Integer academicYear, Integer period, Long schoolId, Long classId, Long studentId) {}
    record SourceMetric(String sourceCode, String sourceLabel, long totalStudents, long studentsWithResults,
                        long assessmentsWithResults, BigDecimal coveragePercent, BigDecimal achievementPercent) {}
}
