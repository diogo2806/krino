package br.com.krino.assessment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.krino.assessment.NetworkAssessmentService.OccurrenceRequest;

@Component
public class AssessmentOccurrenceValidator {

    private final JdbcTemplate jdbcTemplate;

    public AssessmentOccurrenceValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void validate(long assessmentId, OccurrenceRequest request) {
        if (request.schoolId() == null && request.classId() == null) return;
        if (request.classId() != null) {
            Integer classCount = request.schoolId() == null
                    ? jdbcTemplate.queryForObject(
                            "select count(*) from network_assessment_scope where assessment_id = ? and class_id = ?",
                            Integer.class, assessmentId, request.classId())
                    : jdbcTemplate.queryForObject(
                            "select count(*) from network_assessment_scope where assessment_id = ? and class_id = ? and school_id = ?",
                            Integer.class, assessmentId, request.classId(), request.schoolId());
            if (classCount == null || classCount == 0) {
                throw new IllegalArgumentException("A turma informada não pertence ao escopo desta avaliação.");
            }
            return;
        }
        Integer schoolCount = jdbcTemplate.queryForObject(
                "select count(*) from network_assessment_scope where assessment_id = ? and school_id = ?",
                Integer.class, assessmentId, request.schoolId());
        if (schoolCount == null || schoolCount == 0) {
            throw new IllegalArgumentException("A unidade escolar informada não pertence ao escopo desta avaliação.");
        }
    }
}
