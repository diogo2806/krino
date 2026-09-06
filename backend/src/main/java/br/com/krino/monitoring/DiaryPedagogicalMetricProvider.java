package br.com.krino.monitoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiaryPedagogicalMetricProvider implements PedagogicalMetricProvider {

    private final JdbcTemplate jdbcTemplate;

    public DiaryPedagogicalMetricProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String sourceCode() { return "INTERNAL_DIARY"; }

    @Override
    public String sourceLabel() { return "Avaliações internas do Diário de Classe"; }

    @Override
    public SourceMetric load(MetricFilter filter) {
        StringBuilder sql = new StringBuilder(
                "select count(distinct e.student_id) total_students, "
                        + "count(distinct case when g.score is not null then e.student_id end) students_with_results, "
                        + "count(distinct case when g.score is not null then a.id end) assessments_with_results, "
                        + "coalesce(sum(g.score) filter (where g.score is not null and a.max_score is not null), 0) score_sum, "
                        + "coalesce(sum(a.max_score) filter (where g.score is not null and a.max_score is not null), 0) max_sum "
                        + "from student_enrollment e join school_class c on c.id = e.class_id "
                        + "left join class_diary d on d.class_id = c.id and d.active = true "
                        + "left join diary_assessment a on a.diary_id = d.id");
        List<Object> parameters = new ArrayList<>();
        if (filter.period() != null) {
            sql.append(" and a.period = ?");
            parameters.add(filter.period());
        }
        sql.append(" left join diary_assessment_grade g on g.assessment_id = a.id and g.enrollment_id = e.id where e.academic_year = ?");
        parameters.add(filter.academicYear());
        if (filter.schoolId() != null) {
            sql.append(" and c.school_id = ?");
            parameters.add(filter.schoolId());
        }
        if (filter.classId() != null) {
            sql.append(" and c.id = ?");
            parameters.add(filter.classId());
        }

        Aggregate aggregate = jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> new Aggregate(
                rs.getLong("total_students"), rs.getLong("students_with_results"), rs.getLong("assessments_with_results"),
                rs.getBigDecimal("score_sum"), rs.getBigDecimal("max_sum")), parameters.toArray());
        if (aggregate == null) aggregate = new Aggregate(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        return new SourceMetric(sourceCode(), sourceLabel(), aggregate.totalStudents(), aggregate.studentsWithResults(), aggregate.assessmentsWithResults(),
                percentage(BigDecimal.valueOf(aggregate.studentsWithResults()), BigDecimal.valueOf(aggregate.totalStudents())),
                percentage(aggregate.scoreSum(), aggregate.maxSum()));
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return null;
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private record Aggregate(long totalStudents, long studentsWithResults, long assessmentsWithResults, BigDecimal scoreSum, BigDecimal maxSum) {}
}
