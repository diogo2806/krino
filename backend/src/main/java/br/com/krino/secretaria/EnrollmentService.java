package br.com.krino.secretaria;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;

@Service
public class EnrollmentService {

    private final JdbcTemplate jdbcTemplate;
    private final SchoolAccessService accessService;
    private final SecretariaRegistryService registryService;
    private final SecurityAuditService auditService;

    public EnrollmentService(JdbcTemplate jdbcTemplate, SchoolAccessService accessService, SecretariaRegistryService registryService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.registryService = registryService;
        this.auditService = auditService;
    }

    public List<EnrollmentView> list(Long schoolId, int year, Authentication authentication) {
        accessService.requireRead(authentication, schoolId);
        String base = "select e.id, e.student_id, st.registration, st.name student_name, e.class_id, c.name class_name, c.school_id, e.academic_year, e.enrollment_type, e.enrollment_date, e.status, e.previous_enrollment_id "
                + "from student_enrollment e join student st on st.id = e.student_id join school_class c on c.id = e.class_id ";
        if (schoolId == null) return jdbcTemplate.query(base + "where e.academic_year = ? order by st.name", this::mapEnrollment, year);
        return jdbcTemplate.query(base + "where c.school_id = ? and e.academic_year = ? order by st.name", this::mapEnrollment, schoolId, year);
    }

    @Transactional
    public EnrollmentView enroll(EnrollmentRequest request, Authentication authentication) {
        var classView = registryService.getClass(request.classId());
        accessService.requireWrite(authentication, classView.schoolId());
        var student = registryService.getStudent(request.studentId());
        if ("DECEASED".equals(student.status())) throw new IllegalArgumentException("Não é possível matricular estudante registrado como falecido.");

        Integer active = jdbcTemplate.queryForObject("select count(*) from student_enrollment where student_id = ? and academic_year = ? and status = 'ACTIVE'", Integer.class, request.studentId(), classView.academicYear());
        if (active != null && active > 0) throw new IllegalArgumentException("O estudante já possui matrícula ativa neste ano letivo. Registre uma troca de turma quando necessário.");

        String type = request.enrollmentType().trim().toUpperCase();
        if (!(type.equals("ENROLLMENT") || type.equals("REENROLLMENT"))) throw new IllegalArgumentException("Tipo de matrícula inválido.");
        Long previousId = null;
        if (type.equals("REENROLLMENT")) {
            List<Long> previousIds = jdbcTemplate.query(
                    "select id from student_enrollment where student_id = ? and academic_year < ? order by academic_year desc, id desc limit 1",
                    (rs, rowNum) -> rs.getLong("id"), request.studentId(), classView.academicYear());
            previousId = previousIds.isEmpty() ? null : previousIds.getFirst();
        }
        long id = insertEnrollment(request.studentId(), request.classId(), classView.academicYear(), type, request.enrollmentDate(), previousId);
        auditService.record(authentication.getName(), "ENROLLMENT_CREATED", "ENROLLMENT", Long.toString(id), student.registration() + " / " + classView.name());
        return get(id);
    }

    @Transactional
    public MovementResult move(long enrollmentId, MovementRequest request, Authentication authentication) {
        EnrollmentView current = get(enrollmentId);
        accessService.requireWrite(authentication, current.schoolId());
        if (!"ACTIVE".equals(current.status())) throw new IllegalArgumentException("Somente matrícula ativa pode receber nova movimentação.");
        String type = request.movementType().trim().toUpperCase();
        if (!(type.equals("TRANSFER") || type.equals("CLASS_CHANGE") || type.equals("DEATH"))) throw new IllegalArgumentException("Tipo de movimentação inválido.");

        Long newEnrollmentId = null;
        Long destinationClassId = request.destinationClassId();
        if (type.equals("CLASS_CHANGE")) {
            if (destinationClassId == null) throw new IllegalArgumentException("Selecione a turma de destino para a troca de turma.");
            var destination = registryService.getClass(destinationClassId);
            accessService.requireWrite(authentication, destination.schoolId());
            if (!destination.academicYear().equals(current.academicYear())) throw new IllegalArgumentException("A turma de destino deve pertencer ao mesmo ano letivo.");
            if (!destination.schoolId().equals(current.schoolId())) throw new IllegalArgumentException("Para outra unidade escolar, utilize a movimentação Transferência.");
            jdbcTemplate.update("update student_enrollment set status = 'CLASS_CHANGED' where id = ?", enrollmentId);
            newEnrollmentId = insertEnrollment(current.studentId(), destinationClassId, current.academicYear(), "CLASS_CHANGE", request.effectiveDate(), enrollmentId);
        } else if (type.equals("TRANSFER")) {
            jdbcTemplate.update("update student_enrollment set status = 'TRANSFERRED' where id = ?", enrollmentId);
        } else {
            jdbcTemplate.update("update student_enrollment set status = 'DECEASED' where id = ?", enrollmentId);
            jdbcTemplate.update("update student set status = 'DECEASED', updated_at = current_timestamp where id = ?", current.studentId());
        }

        long movementId = insertMovement(enrollmentId, type, request.effectiveDate(), destinationClassId, request.notes(), authentication.getName());
        auditService.record(authentication.getName(), "STUDENT_MOVEMENT_CREATED", "STUDENT_MOVEMENT", Long.toString(movementId), type + " / matrícula " + enrollmentId);
        return new MovementResult(movementId, get(enrollmentId), newEnrollmentId == null ? null : get(newEnrollmentId));
    }

    public List<MovementView> movements(long studentId, Authentication authentication) {
        List<Long> schoolIds = jdbcTemplate.query("select distinct c.school_id from student_enrollment e join school_class c on c.id = e.class_id where e.student_id = ?", (rs, rowNum) -> rs.getLong(1), studentId);
        if (schoolIds.isEmpty()) throw new IllegalArgumentException("O estudante ainda não possui histórico de matrícula.");
        boolean allowed = schoolIds.stream().anyMatch(id -> {
            try { accessService.requireRead(authentication, id); return true; } catch (RuntimeException exception) { return false; }
        });
        if (!allowed) accessService.requireRead(authentication, schoolIds.getFirst());
        return jdbcTemplate.query(
                "select m.id, m.enrollment_id, m.movement_type, m.effective_date, m.destination_class_id, m.notes, m.created_by, m.created_at from student_movement m join student_enrollment e on e.id = m.enrollment_id where e.student_id = ? order by m.effective_date, m.id",
                (rs, rowNum) -> new MovementView(rs.getLong("id"), rs.getLong("enrollment_id"), rs.getString("movement_type"), rs.getDate("effective_date").toLocalDate(), nullableLong(rs, "destination_class_id"), rs.getString("notes"), rs.getString("created_by")),
                studentId);
    }

    public EnrollmentView get(long id) {
        var rows = jdbcTemplate.query(
                "select e.id, e.student_id, st.registration, st.name student_name, e.class_id, c.name class_name, c.school_id, e.academic_year, e.enrollment_type, e.enrollment_date, e.status, e.previous_enrollment_id from student_enrollment e join student st on st.id = e.student_id join school_class c on c.id = e.class_id where e.id = ?",
                this::mapEnrollment, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Matrícula não encontrada.");
        return rows.getFirst();
    }

    private long insertEnrollment(long studentId, long classId, int academicYear, String type, LocalDate date, Long previousId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("insert into student_enrollment (student_id, class_id, academic_year, enrollment_type, enrollment_date, status, previous_enrollment_id) values (?, ?, ?, ?, ?, 'ACTIVE', ?)", new String[]{"id"});
            statement.setLong(1, studentId); statement.setLong(2, classId); statement.setInt(3, academicYear); statement.setString(4, type); statement.setDate(5, Date.valueOf(date)); statement.setObject(6, previousId); return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private long insertMovement(long enrollmentId, String type, LocalDate date, Long destinationClassId, String notes, String actor) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("insert into student_movement (enrollment_id, movement_type, effective_date, destination_class_id, notes, created_by) values (?, ?, ?, ?, ?, ?)", new String[]{"id"});
            statement.setLong(1, enrollmentId); statement.setString(2, type); statement.setDate(3, Date.valueOf(date)); statement.setObject(4, destinationClassId); statement.setString(5, notes); statement.setString(6, actor); return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private EnrollmentView mapEnrollment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new EnrollmentView(rs.getLong("id"), rs.getLong("student_id"), rs.getString("registration"), rs.getString("student_name"), rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("school_id"), rs.getInt("academic_year"), rs.getString("enrollment_type"), rs.getDate("enrollment_date").toLocalDate(), rs.getString("status"), nullableLong(rs, "previous_enrollment_id"));
    }

    private Long nullableLong(java.sql.ResultSet rs, String name) throws java.sql.SQLException { long value = rs.getLong(name); return rs.wasNull() ? null : value; }

    public record EnrollmentRequest(@NotNull(message = "Selecione o estudante.") Long studentId, @NotNull(message = "Selecione a turma.") Long classId, @NotBlank(message = "Selecione o tipo de matrícula.") String enrollmentType, @NotNull(message = "Informe a data da matrícula.") LocalDate enrollmentDate) {}
    public record MovementRequest(@NotBlank(message = "Selecione o tipo de movimentação.") String movementType, @NotNull(message = "Informe a data de efeito da movimentação.") LocalDate effectiveDate, Long destinationClassId, String notes) {}
    public record EnrollmentView(Long id, Long studentId, String registration, String studentName, Long classId, String className, Long schoolId, Integer academicYear, String enrollmentType, LocalDate enrollmentDate, String status, Long previousEnrollmentId) {}
    public record MovementView(Long id, Long enrollmentId, String movementType, LocalDate effectiveDate, Long destinationClassId, String notes, String createdBy) {}
    public record MovementResult(Long movementId, EnrollmentView previousEnrollment, EnrollmentView newEnrollment) {}
}
