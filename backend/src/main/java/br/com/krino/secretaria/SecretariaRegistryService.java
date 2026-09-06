package br.com.krino.secretaria;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
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
public class SecretariaRegistryService {

    private final JdbcTemplate jdbcTemplate;
    private final SchoolAccessService accessService;
    private final SecurityAuditService auditService;

    public SecretariaRegistryService(JdbcTemplate jdbcTemplate, SchoolAccessService accessService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    public List<SchoolView> listSchools(Authentication authentication) {
        List<Long> ids = accessService.accessibleSchoolIds(authentication, "SCHOOL_READ");
        if (ids.isEmpty()) return List.of();
        return jdbcTemplate.query(
                "select id, code, name, address, active from school_unit where id = any(?) order by name",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray())),
                (rs, rowNum) -> new SchoolView(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("address"), rs.getBoolean("active")));
    }

    @Transactional
    public SchoolView createSchool(SchoolRequest request, Authentication authentication) {
        accessService.requireNetworkWrite(authentication);
        long id = insertReturningId("insert into school_unit (code, name, address, active) values (?, ?, ?, true)", request.code().trim(), request.name().trim(), request.address());
        auditService.record(authentication.getName(), "SCHOOL_CREATED", "SCHOOL", Long.toString(id), request.code().trim());
        return getSchool(id);
    }

    @Transactional
    public SchoolView updateSchool(long id, SchoolRequest request, Authentication authentication) {
        accessService.requireWrite(authentication, id);
        int updated = jdbcTemplate.update("update school_unit set code = ?, name = ?, address = ?, updated_at = current_timestamp where id = ?", request.code().trim(), request.name().trim(), request.address(), id);
        if (updated == 0) throw new IllegalArgumentException("Unidade escolar não encontrada.");
        auditService.record(authentication.getName(), "SCHOOL_UPDATED", "SCHOOL", Long.toString(id), request.code().trim());
        return getSchool(id);
    }

    public List<StudentView> listStudents(Long schoolId, Integer year, String search, Authentication authentication) {
        accessService.requireRead(authentication, schoolId);
        String term = search == null ? "" : search.trim().toLowerCase();
        if (schoolId == null) {
            return jdbcTemplate.query(
                    "select distinct s.id, s.registration, s.name, s.birth_date, s.guardian_name, s.guardian_profession, s.status "
                            + "from student s left join student_enrollment e on e.student_id = s.id "
                            + "where (? = '' or lower(s.name) like ? or lower(s.registration) like ?) "
                            + "and (? is null or e.academic_year = ?) order by s.name",
                    this::mapStudent, term, "%" + term + "%", "%" + term + "%", year, year);
        }
        return jdbcTemplate.query(
                "select distinct s.id, s.registration, s.name, s.birth_date, s.guardian_name, s.guardian_profession, s.status "
                        + "from student s left join student_enrollment e on e.student_id = s.id left join school_class c on c.id = e.class_id "
                        + "where (s.origin_school_id = ? or c.school_id = ?) "
                        + "and (? = '' or lower(s.name) like ? or lower(s.registration) like ?) "
                        + "and (? is null or e.academic_year = ? or e.id is null) order by s.name",
                this::mapStudent, schoolId, schoolId, term, "%" + term + "%", "%" + term + "%", year, year);
    }

    @Transactional
    public StudentView createStudent(StudentRequest request, Authentication authentication) {
        accessService.requireWrite(authentication, request.schoolId());
        long id = insertReturningId(
                "insert into student (origin_school_id, registration, name, birth_date, guardian_name, guardian_profession) values (?, ?, ?, ?, ?, ?)",
                request.schoolId(), request.registration().trim(), request.name().trim(), request.birthDate(), request.guardianName(), request.guardianProfession());
        auditService.record(authentication.getName(), "STUDENT_CREATED", "STUDENT", Long.toString(id), request.registration().trim());
        return getStudent(id);
    }

    @Transactional
    public StudentView updateStudent(long id, StudentRequest request, Authentication authentication) {
        accessService.requireWrite(authentication, request.schoolId());
        int updated = jdbcTemplate.update(
                "update student set origin_school_id = ?, registration = ?, name = ?, birth_date = ?, guardian_name = ?, guardian_profession = ?, updated_at = current_timestamp where id = ?",
                request.schoolId(), request.registration().trim(), request.name().trim(), request.birthDate(), request.guardianName(), request.guardianProfession(), id);
        if (updated == 0) throw new IllegalArgumentException("Estudante não encontrado.");
        auditService.record(authentication.getName(), "STUDENT_UPDATED", "STUDENT", Long.toString(id), request.registration().trim());
        return getStudent(id);
    }

    public List<ProfessionalView> listProfessionals(Long schoolId, String search, Authentication authentication) {
        accessService.requireRead(authentication, schoolId);
        String term = search == null ? "" : search.trim().toLowerCase();
        if (schoolId == null) {
            return jdbcTemplate.query("select id, registration, name, professional_type, active, origin_school_id from education_professional where (? = '' or lower(name) like ? or lower(registration) like ?) order by name",
                    this::mapProfessional, term, "%" + term + "%", "%" + term + "%");
        }
        return jdbcTemplate.query("select id, registration, name, professional_type, active, origin_school_id from education_professional where origin_school_id = ? and (? = '' or lower(name) like ? or lower(registration) like ?) order by name",
                this::mapProfessional, schoolId, term, "%" + term + "%", "%" + term + "%");
    }

    @Transactional
    public ProfessionalView createProfessional(ProfessionalRequest request, Authentication authentication) {
        accessService.requireWrite(authentication, request.schoolId());
        long id = insertReturningId("insert into education_professional (origin_school_id, registration, name, professional_type, active) values (?, ?, ?, ?, true)",
                request.schoolId(), request.registration().trim(), request.name().trim(), request.professionalType().trim());
        auditService.record(authentication.getName(), "PROFESSIONAL_CREATED", "PROFESSIONAL", Long.toString(id), request.registration().trim());
        return getProfessional(id);
    }

    @Transactional
    public ProfessionalView updateProfessional(long id, ProfessionalRequest request, Authentication authentication) {
        accessService.requireWrite(authentication, request.schoolId());
        int updated = jdbcTemplate.update("update education_professional set origin_school_id = ?, registration = ?, name = ?, professional_type = ?, updated_at = current_timestamp where id = ?",
                request.schoolId(), request.registration().trim(), request.name().trim(), request.professionalType().trim(), id);
        if (updated == 0) throw new IllegalArgumentException("Profissional da educação não encontrado.");
        auditService.record(authentication.getName(), "PROFESSIONAL_UPDATED", "PROFESSIONAL", Long.toString(id), request.registration().trim());
        return getProfessional(id);
    }

    public List<ClassView> listClasses(Long schoolId, int year, Authentication authentication) {
        accessService.requireRead(authentication, schoolId);
        if (schoolId == null) {
            return jdbcTemplate.query("select c.id, c.school_id, s.name school_name, c.academic_year, c.name, c.stage, c.shift, c.active from school_class c join school_unit s on s.id = c.school_id where c.academic_year = ? order by s.name, c.name", this::mapClass, year);
        }
        return jdbcTemplate.query("select c.id, c.school_id, s.name school_name, c.academic_year, c.name, c.stage, c.shift, c.active from school_class c join school_unit s on s.id = c.school_id where c.school_id = ? and c.academic_year = ? order by c.name", this::mapClass, schoolId, year);
    }

    @Transactional
    public ClassView createClass(ClassRequest request, Authentication authentication) {
        accessService.requireWrite(authentication, request.schoolId());
        long id = insertReturningId("insert into school_class (school_id, academic_year, name, stage, shift, active) values (?, ?, ?, ?, ?, true)",
                request.schoolId(), request.academicYear(), request.name().trim(), request.stage().trim(), request.shift().trim());
        auditService.record(authentication.getName(), "CLASS_CREATED", "CLASS", Long.toString(id), request.name().trim());
        return getClass(id);
    }

    @Transactional
    public ClassView updateClass(long id, ClassRequest request, Authentication authentication) {
        accessService.requireWrite(authentication, request.schoolId());
        int updated = jdbcTemplate.update("update school_class set school_id = ?, academic_year = ?, name = ?, stage = ?, shift = ?, updated_at = current_timestamp where id = ?",
                request.schoolId(), request.academicYear(), request.name().trim(), request.stage().trim(), request.shift().trim(), id);
        if (updated == 0) throw new IllegalArgumentException("Turma não encontrada.");
        auditService.record(authentication.getName(), "CLASS_UPDATED", "CLASS", Long.toString(id), request.name().trim());
        return getClass(id);
    }

    public List<ComponentView> listComponents(Authentication authentication) {
        if (!authentication.isAuthenticated()) throw new IllegalArgumentException("Autenticação necessária.");
        return jdbcTemplate.query("select id, code, name from curricular_component where active = true order by name",
                (rs, rowNum) -> new ComponentView(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
    }

    public SchoolView getSchool(long id) {
        var rows = jdbcTemplate.query("select id, code, name, address, active from school_unit where id = ?", (rs, rowNum) -> new SchoolView(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("address"), rs.getBoolean("active")), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unidade escolar não encontrada.");
        return rows.getFirst();
    }

    public StudentView getStudent(long id) {
        var rows = jdbcTemplate.query("select id, registration, name, birth_date, guardian_name, guardian_profession, status from student where id = ?", this::mapStudent, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Estudante não encontrado.");
        return rows.getFirst();
    }

    public ProfessionalView getProfessional(long id) {
        var rows = jdbcTemplate.query("select id, registration, name, professional_type, active, origin_school_id from education_professional where id = ?", this::mapProfessional, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Profissional da educação não encontrado.");
        return rows.getFirst();
    }

    public ClassView getClass(long id) {
        var rows = jdbcTemplate.query("select c.id, c.school_id, s.name school_name, c.academic_year, c.name, c.stage, c.shift, c.active from school_class c join school_unit s on s.id = c.school_id where c.id = ?", this::mapClass, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Turma não encontrada.");
        return rows.getFirst();
    }

    private StudentView mapStudent(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Date birthDate = rs.getDate("birth_date");
        return new StudentView(rs.getLong("id"), rs.getString("registration"), rs.getString("name"), birthDate == null ? null : birthDate.toLocalDate(), rs.getString("guardian_name"), rs.getString("guardian_profession"), rs.getString("status"));
    }

    private ProfessionalView mapProfessional(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        long schoolId = rs.getLong("origin_school_id");
        return new ProfessionalView(rs.getLong("id"), schoolId == 0 && rs.wasNull() ? null : schoolId, rs.getString("registration"), rs.getString("name"), rs.getString("professional_type"), rs.getBoolean("active"));
    }

    private ClassView mapClass(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ClassView(rs.getLong("id"), rs.getLong("school_id"), rs.getString("school_name"), rs.getInt("academic_year"), rs.getString("name"), rs.getString("stage"), rs.getString("shift"), rs.getBoolean("active"));
    }

    private long insertReturningId(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                Object value = values[index];
                if (value instanceof LocalDate localDate) statement.setDate(index + 1, Date.valueOf(localDate));
                else statement.setObject(index + 1, value);
            }
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public record SchoolRequest(@NotBlank(message = "Informe o código da unidade escolar.") String code, @NotBlank(message = "Informe o nome da unidade escolar.") String name, String address) {}
    public record StudentRequest(@NotNull(message = "Selecione a unidade escolar de referência.") Long schoolId, @NotBlank(message = "Informe a matrícula do estudante.") String registration, @NotBlank(message = "Informe o nome do estudante.") String name, LocalDate birthDate, String guardianName, String guardianProfession) {}
    public record ProfessionalRequest(@NotNull(message = "Selecione a unidade escolar de referência.") Long schoolId, @NotBlank(message = "Informe a matrícula funcional.") String registration, @NotBlank(message = "Informe o nome do profissional.") String name, @NotBlank(message = "Informe a função do profissional.") String professionalType) {}
    public record ClassRequest(@NotNull(message = "Selecione a unidade escolar.") Long schoolId, @NotNull(message = "Informe o ano letivo.") Integer academicYear, @NotBlank(message = "Informe o nome da turma.") String name, @NotBlank(message = "Informe a etapa/ano da turma.") String stage, @NotBlank(message = "Informe o turno.") String shift) {}

    public record SchoolView(Long id, String code, String name, String address, boolean active) {}
    public record StudentView(Long id, String registration, String name, LocalDate birthDate, String guardianName, String guardianProfession, String status) {}
    public record ProfessionalView(Long id, Long schoolId, String registration, String name, String professionalType, boolean active) {}
    public record ClassView(Long id, Long schoolId, String schoolName, Integer academicYear, String name, String stage, String shift, boolean active) {}
    public record ComponentView(Long id, String code, String name) {}
}
