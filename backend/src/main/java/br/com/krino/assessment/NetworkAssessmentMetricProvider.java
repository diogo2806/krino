package br.com.krino.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.krino.monitoring.PedagogicalMetricProvider;

@Component
public class NetworkAssessmentMetricProvider implements PedagogicalMetricProvider {

    private final JdbcTemplate jdbcTemplate;

    public NetworkAssessmentMetricProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String sourceCode() { return "NETWORK_ASSESSMENT"; }

    @Override
    public String sourceLabel() { return "Avaliações Educacionais em Rede"; }

    @Override
    public SourceMetric load(MetricFilter filter) {
        StringBuilder sql = new StringBuilder(
                "with latest_run as (select assessment_id, max(id) run_id from network_assessment_processing_run where status = 'COMPLETED' group by assessment_id) "
                        + "select count(distinct aa.student_id) total_students, "
                        + "count(distinct case when r.id is not null then aa.student_id end) students_with_results, "
                        + "count(distinct case when r.id is not null then na.id end) assessments_with_results, "
                        + "coalesce(sum(r.correct_answers), 0) correct_sum, coalesce(sum(r.total_questions), 0) total_sum "
                        + "from network_assessment na join network_assessment_assignment aa on aa.assessment_id = na.id "
                        + "left join latest_run lr on lr.assessment_id = na.id "
                        + "left join network_assessment_result r on r.processing_run_id = lr.run_id and r.assignment_id = aa.id where 1=1");
        List<Object> parameters = new ArrayList<>();
        if (filter.academicYear() != null) { sql.append(" and na.academic_year = ?"); parameters.add(filter.academicYear()); }
        if (filter.schoolId() != null) { sql.append(" and aa.school_id = ?"); parameters.add(filter.schoolId()); }
        if (filter.classId() != null) { sql.append(" and aa.class_id = ?"); parameters.add(filter.classId()); }
        if (filter.studentId() != null) { sql.append(" and aa.student_id = ?"); parameters.add(filter.studentId()); }
        Aggregate aggregate = jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> new Aggregate(
                rs.getLong("total_students"), rs.getLong("students_with_results"), rs.getLong("assessments_with_results"),
                rs.getBigDecimal("correct_sum"), rs.getBigDecimal("total_sum")), parameters.toArray());
        if (aggregate == null) aggregate = new Aggregate(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        return new SourceMetric(sourceCode(), sourceLabel(), aggregate.totalStudents(), aggregate.studentsWithResults(), aggregate.assessmentsWithResults(),
                percentage(BigDecimal.valueOf(aggregate.studentsWithResults()), BigDecimal.valueOf(aggregate.totalStudents())),
                percentage(aggregate.correctSum(), aggregate.totalSum()));
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return null;
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private record Aggregate(long totalStudents, long studentsWithResults, long assessmentsWithResults, BigDecimal correctSum, BigDecimal totalSum) {}
}
