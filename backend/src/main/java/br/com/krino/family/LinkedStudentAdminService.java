package br.com.krino.family;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.security.AuthorizationService;

@Service
public class LinkedStudentAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;
    private final SecurityAuditService auditService;

    public LinkedStudentAdminService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<StudentOption> linkedStudents(long userId) {
        requireUser(userId);
        return jdbcTemplate.query(
                "select st.id, st.registration, st.name, s.name school_name, c.name class_name "
                        + "from linked_resource_access lra join student st on st.id::text = lra.resource_reference "
                        + "left join student_enrollment e on e.student_id = st.id and e.status = 'ACTIVE' "
                        + "left join school_class c on c.id = e.class_id left join school_unit s on s.id = c.school_id "
                        + "where lra.user_id = ? and lra.resource_type = 'STUDENT' order by st.name",
                (rs, rowNum) -> new StudentOption(rs.getLong("id"), rs.getString("registration"), rs.getString("name"), rs.getString("school_name"), rs.getString("class_name")), userId);
    }

    public List<StudentOption> catalog(long userId, String search) {
        requireUser(userId);
        String term = search == null ? "" : search.trim();
        return jdbcTemplate.query(
                "select st.id, st.registration, st.name, s.name school_name, c.name class_name "
                        + "from student st left join student_enrollment e on e.student_id = st.id and e.status = 'ACTIVE' "
                        + "left join school_class c on c.id = e.class_id left join school_unit s on s.id = c.school_id "
                        + "where st.status = 'ACTIVE' and (? = '' or lower(st.name) like lower(?) or lower(st.registration) like lower(?)) "
                        + "order by st.name limit 100",
                (rs, rowNum) -> new StudentOption(rs.getLong("id"), rs.getString("registration"), rs.getString("name"), rs.getString("school_name"), rs.getString("class_name")),
                term, "%" + term + "%", "%" + term + "%");
    }

    @Transactional
    public void link(long userId, long studentId, String actor) {
        requireUser(userId);
        requireStudent(studentId);
        requireResponsiblePermission(userId);
        authorizationService.linkResource(userId, "STUDENT", Long.toString(studentId), "READ");
        auditService.record(actor, "RESPONSIBLE_STUDENT_LINKED", "USER", Long.toString(userId), "Estudante vinculado: " + studentId);
    }

    @Transactional
    public void unlink(long userId, long studentId, String actor) {
        requireUser(userId);
        authorizationService.unlinkResource(userId, "STUDENT", Long.toString(studentId));
        auditService.record(actor, "RESPONSIBLE_STUDENT_UNLINKED", "USER", Long.toString(userId), "Estudante desvinculado: " + studentId);
    }

    private void requireResponsiblePermission(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_role_assignment ura join access_role_permission rp on rp.role_id = ura.role_id "
                        + "join access_permission p on p.id = rp.permission_id where ura.user_id = ? and p.code = 'STUDENT_LINKED_READ'",
                Integer.class, userId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("A conta precisa possuir um perfil com acesso ao Portal do Responsável antes de vincular estudantes.");
        }
    }

    private void requireUser(long userId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from app_user where id = ? and active = true", Integer.class, userId);
        if (count == null || count == 0) throw new IllegalArgumentException("Usuário não encontrado ou inativo.");
    }

    private void requireStudent(long studentId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from student where id = ? and status = 'ACTIVE'", Integer.class, studentId);
        if (count == null || count == 0) throw new IllegalArgumentException("Estudante não encontrado ou inativo.");
    }

    public record StudentOption(Long id, String registration, String name, String schoolName, String className) {}
}
