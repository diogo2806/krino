package br.com.krino.assessment;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.assessment.NetworkAssessmentService.OccurrenceView;

@Service
public class AssessmentOccurrenceQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final AssessmentAccessService accessService;

    public AssessmentOccurrenceQueryService(JdbcTemplate jdbcTemplate, AssessmentAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public List<OccurrenceView> list(long assessmentId, Authentication authentication) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(assessmentId);
        StringBuilder sql = new StringBuilder(
                "select o.id, o.school_id, su.name school_name, o.class_id, sc.name class_name, o.occurred_at, o.description, o.created_by "
                        + "from network_assessment_occurrence o left join school_unit su on su.id = o.school_id "
                        + "left join school_class sc on sc.id = o.class_id where o.assessment_id = ?");
        if (!accessService.hasNetworkRead(authentication)) {
            List<Long> schoolIds = accessService.readableSchoolIds(authentication);
            if (schoolIds.isEmpty()) throw new AccessDeniedException("Sua conta não possui acesso às ocorrências desta avaliação.");
            sql.append(" and o.school_id in (").append(placeholders(schoolIds.size())).append(")");
            parameters.addAll(schoolIds);
        }
        sql.append(" order by o.occurred_at desc, o.id desc");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new OccurrenceView(
                rs.getLong("id"), nullableLong(rs, "school_id"), rs.getString("school_name"),
                nullableLong(rs, "class_id"), rs.getString("class_name"), rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getString("description"), rs.getString("created_by")), parameters.toArray());
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
