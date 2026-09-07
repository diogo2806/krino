package br.com.krino.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ReportFilterService {

    private final JdbcTemplate jdbcTemplate;
    private final ReportAccessService accessService;

    public ReportFilterService(JdbcTemplate jdbcTemplate, ReportAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public ReportFilters filters(long assessmentId, Long schoolId, Authentication authentication) {
        List<Object> params = new ArrayList<>();
        params.add(assessmentId);
        StringBuilder scope = new StringBuilder();
        if (schoolId != null) {
            accessService.requireRead(authentication, schoolId);
            scope.append(" and aa.school_id = ?");
            params.add(schoolId);
        } else if (!accessService.hasNetworkRead(authentication)) {
            List<Long> schoolIds = accessService.readableSchoolIds(authentication);
            if (schoolIds.isEmpty()) throw new AccessDeniedException("Sua conta não possui unidade escolar autorizada para relatórios.");
            scope.append(" and aa.school_id in (").append(placeholders(schoolIds.size())).append(")");
            params.addAll(schoolIds);
        }
        List<ClassOption> classes = jdbcTemplate.query(
                "select distinct aa.class_id, sc.name, aa.school_id from network_assessment_assignment aa "
                        + "join school_class sc on sc.id = aa.class_id where aa.assessment_id = ?" + scope + " order by sc.name",
                (rs, rowNum) -> new ClassOption(rs.getLong("class_id"), rs.getLong("school_id"), rs.getString("name")), params.toArray());
        List<StudentOption> students = jdbcTemplate.query(
                "select distinct aa.student_id, s.registration, s.name, aa.class_id, aa.school_id from network_assessment_assignment aa "
                        + "join student s on s.id = aa.student_id where aa.assessment_id = ?" + scope + " order by s.name",
                (rs, rowNum) -> new StudentOption(rs.getLong("student_id"), rs.getString("registration"), rs.getString("name"),
                        rs.getLong("class_id"), rs.getLong("school_id")), params.toArray());
        return new ReportFilters(classes, students);
    }

    public void requireStudentVisible(long assessmentId, long studentId, Long schoolId, Long classId, Authentication authentication) {
        boolean visible = filters(assessmentId, schoolId, authentication).students().stream()
                .anyMatch(student -> student.id() == studentId && (classId == null || student.classId() == classId));
        if (!visible) {
            throw new AccessDeniedException("O estudante informado não pertence ao escopo autorizado deste relatório.");
        }
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    public record ReportFilters(List<ClassOption> classes, List<StudentOption> students) {}
    public record ClassOption(long id, long schoolId, String name) {}
    public record StudentOption(long id, String registration, String name, long classId, long schoolId) {}
}
