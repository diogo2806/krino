package br.com.krino.diary;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;

@Service
public class DiaryService {

    private final JdbcTemplate jdbcTemplate;
    private final DiaryAccessService accessService;
    private final SecurityAuditService auditService;

    public DiaryService(JdbcTemplate jdbcTemplate, DiaryAccessService accessService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    public List<DiaryView> list(long classId, Authentication authentication) {
        ClassCore classCore = classCore(classId);
        accessService.requireSchoolRead(classCore.schoolId(), authentication);
        return jdbcTemplate.query(
                "select d.id, d.class_id, c.name class_name, c.school_id, s.name school_name, d.component_id, cc.name component_name, d.mode, d.responsible_professional_id, p.name responsible_professional_name, d.valid_from, d.valid_until, d.active "
                        + "from class_diary d join school_class c on c.id = d.class_id join school_unit s on s.id = c.school_id left join curricular_component cc on cc.id = d.component_id join education_professional p on p.id = d.responsible_professional_id where d.class_id = ? and d.active = true order by cc.name nulls first, d.valid_from",
                (rs, rowNum) -> mapDiary(rs, authentication), classId);
    }

    public DiaryView get(long diaryId, Authentication authentication) {
        accessService.requireRead(diaryId, authentication);
        List<DiaryView> rows = jdbcTemplate.query(
                "select d.id, d.class_id, c.name class_name, c.school_id, s.name school_name, d.component_id, cc.name component_name, d.mode, d.responsible_professional_id, p.name responsible_professional_name, d.valid_from, d.valid_until, d.active "
                        + "from class_diary d join school_class c on c.id = d.class_id join school_unit s on s.id = c.school_id left join curricular_component cc on cc.id = d.component_id join education_professional p on p.id = d.responsible_professional_id where d.id = ?",
                (rs, rowNum) -> mapDiary(rs, authentication), diaryId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Diário de Classe não encontrado.");
        return rows.getFirst();
    }

    @Transactional
    public DiaryView create(CreateDiaryRequest request, Authentication authentication) {
        ClassCore classCore = classCore(request.classId());
        accessService.requireSchoolAdmin(classCore.schoolId(), authentication);
        String mode = normalizeMode(request.mode());
        if ((mode.equals("FINAL_YEARS") || mode.equals("EJA")) && request.componentId() == null) {
            throw new IllegalArgumentException("Anos Finais e EJA exigem componente curricular no Diário de Classe.");
        }
        if (request.validUntil() != null && request.validUntil().isBefore(request.validFrom())) {
            throw new IllegalArgumentException("A vigência final do diário não pode ser anterior à inicial.");
        }
        ensureProfessional(request.responsibleProfessionalId());
        ensureComponent(request.componentId());
        ensureTeacherAssignment(request.classId(), request.componentId(), request.responsibleProfessionalId(), request.validFrom());
        Integer overlap = jdbcTemplate.queryForObject(
                "select count(*) from class_diary where class_id = ? and coalesce(component_id, 0) = coalesce(?, 0) and active = true and (valid_until is null or valid_until >= ?) and (? is null or valid_from <= ?)",
                Integer.class, request.classId(), request.componentId(), request.validFrom(), request.validUntil(), request.validUntil());
        if (overlap != null && overlap > 0) throw new IllegalArgumentException("Já existe Diário de Classe com vigência sobreposta para esta turma e componente.");

        Long id = jdbcTemplate.queryForObject(
                "insert into class_diary (class_id, component_id, mode, responsible_professional_id, valid_from, valid_until) values (?, ?, ?, ?, ?, ?) returning id",
                Long.class, request.classId(), request.componentId(), mode, request.responsibleProfessionalId(), request.validFrom(), request.validUntil());
        auditService.record(authentication.getName(), "DIARY_CREATED", "DIARY", Long.toString(id), classCore.name());
        return get(id, authentication);
    }

    public List<RosterStudent> roster(long diaryId, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.requireRead(diaryId, authentication);
        return jdbcTemplate.query(
                "select e.id enrollment_id, st.id student_id, st.registration, st.name from student_enrollment e join student st on st.id = e.student_id where e.class_id = ? and e.status = 'ACTIVE' order by st.name",
                (rs, rowNum) -> new RosterStudent(rs.getLong("enrollment_id"), rs.getLong("student_id"), rs.getString("registration"), rs.getString("name")), context.classId());
    }

    public List<LessonView> lessons(long diaryId, LocalDate from, LocalDate to, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.requireRead(diaryId, authentication);
        LocalDate start = from == null ? context.validFrom() : from;
        LocalDate end = to == null ? (context.validUntil() == null ? start.plusMonths(3) : context.validUntil()) : to;
        if (end.isBefore(start)) throw new IllegalArgumentException("O período final da consulta não pode ser anterior ao inicial.");
        return jdbcTemplate.query(
                "select id, diary_id, lesson_date, lesson_slot, content, planning_notes, created_by, updated_by from diary_lesson where diary_id = ? and lesson_date between ? and ? order by lesson_date, lesson_slot",
                (rs, rowNum) -> toLessonView(rs.getLong("id"), rs.getLong("diary_id"), rs.getDate("lesson_date").toLocalDate(), rs.getInt("lesson_slot"), rs.getString("content"), rs.getString("planning_notes"), rs.getString("created_by"), rs.getString("updated_by")),
                diaryId, start, end);
    }

    @Transactional
    public LessonView saveLesson(long diaryId, LocalDate date, int slot, SaveLessonRequest request, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.requireEdit(diaryId, authentication);
        if (slot < 1) throw new IllegalArgumentException("O número da aula deve ser maior que zero.");
        validateTeachingDate(context, date);
        Long lessonId = jdbcTemplate.queryForObject(
                "insert into diary_lesson (diary_id, lesson_date, lesson_slot, content, planning_notes, created_by, updated_by) values (?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (diary_id, lesson_date, lesson_slot) do update set content = excluded.content, planning_notes = excluded.planning_notes, updated_by = excluded.updated_by, updated_at = current_timestamp returning id",
                Long.class, diaryId, date, slot, request.content(), request.planningNotes(), authentication.getName(), authentication.getName());
        if (request.attendance() != null) {
            for (AttendanceInput attendance : request.attendance()) {
                ensureEnrollment(context.classId(), attendance.enrollmentId());
                String status = normalizeAttendance(attendance.status());
                jdbcTemplate.update(
                        "insert into diary_attendance (lesson_id, enrollment_id, attendance_status) values (?, ?, ?) on conflict (lesson_id, enrollment_id) do update set attendance_status = excluded.attendance_status, updated_at = current_timestamp",
                        lessonId, attendance.enrollmentId(), status);
            }
        }
        auditService.record(authentication.getName(), "DIARY_LESSON_SAVED", "DIARY", Long.toString(diaryId), date + " / aula " + slot);
        return toLessonView(lessonId, diaryId, date, slot, request.content(), request.planningNotes(), authentication.getName(), authentication.getName());
    }

    public void validateTeachingDate(DiaryAccessService.DiaryContext context, LocalDate date) {
        if (date.isBefore(context.validFrom()) || (context.validUntil() != null && date.isAfter(context.validUntil()))) {
            throw new IllegalArgumentException("A data informada está fora da vigência deste diário.");
        }
        Integer schoolDay = jdbcTemplate.queryForObject(
                "select count(*) from school_calendar_day where school_id = ? and academic_date = ? and school_day = true",
                Integer.class, context.schoolId(), date);
        if (schoolDay == null || schoolDay == 0) {
            throw new IllegalArgumentException("Não é possível lançar o diário nesta data: não é um dia letivo no calendário escolar.");
        }
        String assignmentSql = context.componentId() == null
                ? "select count(*) from teacher_assignment where class_id = ? and professional_id = ? and valid_from <= ? and (valid_until is null or valid_until >= ?)"
                : "select count(*) from teacher_assignment where class_id = ? and professional_id = ? and component_id = ? and valid_from <= ? and (valid_until is null or valid_until >= ?)";
        Integer assigned = context.componentId() == null
                ? jdbcTemplate.queryForObject(assignmentSql, Integer.class, context.classId(), context.responsibleProfessionalId(), date, date)
                : jdbcTemplate.queryForObject(assignmentSql, Integer.class, context.classId(), context.responsibleProfessionalId(), context.componentId(), date, date);
        if (assigned == null || assigned == 0) {
            throw new IllegalArgumentException("Não é possível lançar o diário nesta data: o professor responsável não possui atribuição vigente para a turma e o componente.");
        }
        if (context.mode().equals("FINAL_YEARS") || context.mode().equals("EJA")) {
            Integer scheduled = jdbcTemplate.queryForObject(
                    "select count(*) from class_schedule where class_id = ? and component_id = ? and day_of_week = ? and valid_from <= ? and (valid_until is null or valid_until >= ?) and (professional_id is null or professional_id = ?)",
                    Integer.class, context.classId(), context.componentId(), date.getDayOfWeek().getValue(), date, date, context.responsibleProfessionalId());
            if (scheduled == null || scheduled == 0) {
                throw new IllegalArgumentException("Não é possível lançar o diário nesta data: este componente não possui aula prevista para a turma no horário semanal vigente.");
            }
        }
    }

    private LessonView toLessonView(long lessonId, long diaryId, LocalDate date, int slot, String content, String planningNotes, String createdBy, String updatedBy) {
        List<AttendanceView> attendance = jdbcTemplate.query(
                "select a.enrollment_id, st.id student_id, st.name student_name, a.attendance_status from diary_attendance a join student_enrollment e on e.id = a.enrollment_id join student st on st.id = e.student_id where a.lesson_id = ? order by st.name",
                (rs, rowNum) -> new AttendanceView(rs.getLong("enrollment_id"), rs.getLong("student_id"), rs.getString("student_name"), rs.getString("attendance_status")), lessonId);
        return new LessonView(lessonId, diaryId, date, slot, content, planningNotes, createdBy, updatedBy, attendance);
    }

    private DiaryView mapDiary(java.sql.ResultSet rs, Authentication authentication) throws java.sql.SQLException {
        java.sql.Date until = rs.getDate("valid_until");
        long id = rs.getLong("id");
        return new DiaryView(id, rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("school_id"), rs.getString("school_name"), nullableLong(rs, "component_id"), rs.getString("component_name"), rs.getString("mode"), rs.getLong("responsible_professional_id"), rs.getString("responsible_professional_name"), rs.getDate("valid_from").toLocalDate(), until == null ? null : until.toLocalDate(), rs.getBoolean("active"), accessService.canEdit(id, authentication));
    }

    private ClassCore classCore(long classId) {
        List<ClassCore> rows = jdbcTemplate.query("select id, school_id, name from school_class where id = ? and active = true", (rs, rowNum) -> new ClassCore(rs.getLong("id"), rs.getLong("school_id"), rs.getString("name")), classId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Turma não encontrada.");
        return rows.getFirst();
    }

    private void ensureProfessional(long professionalId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from education_professional where id = ? and active = true", Integer.class, professionalId);
        if (count == null || count == 0) throw new IllegalArgumentException("Selecione um profissional da educação ativo.");
    }

    private void ensureComponent(Long componentId) {
        if (componentId == null) return;
        Integer count = jdbcTemplate.queryForObject("select count(*) from curricular_component where id = ? and active = true", Integer.class, componentId);
        if (count == null || count == 0) throw new IllegalArgumentException("Componente curricular não encontrado ou inativo.");
    }

    private void ensureTeacherAssignment(long classId, Long componentId, long professionalId, LocalDate referenceDate) {
        String sql = componentId == null
                ? "select count(*) from teacher_assignment where class_id = ? and professional_id = ? and valid_from <= ? and (valid_until is null or valid_until >= ?)"
                : "select count(*) from teacher_assignment where class_id = ? and component_id = ? and professional_id = ? and valid_from <= ? and (valid_until is null or valid_until >= ?)";
        Integer count = componentId == null
                ? jdbcTemplate.queryForObject(sql, Integer.class, classId, professionalId, referenceDate, referenceDate)
                : jdbcTemplate.queryForObject(sql, Integer.class, classId, componentId, professionalId, referenceDate, referenceDate);
        if (count == null || count == 0) throw new IllegalArgumentException("O professor responsável precisa possuir atribuição vigente para a turma e o componente na data inicial do diário.");
    }

    private void ensureEnrollment(long classId, long enrollmentId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from student_enrollment where id = ? and class_id = ? and status = 'ACTIVE'", Integer.class, enrollmentId, classId);
        if (count == null || count == 0) throw new IllegalArgumentException("A frequência contém estudante sem matrícula ativa nesta turma.");
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toUpperCase();
        return switch (normalized) {
            case "EARLY_CHILDHOOD", "LITERACY", "EARLY_YEARS", "FINAL_YEARS", "EJA" -> normalized;
            default -> throw new IllegalArgumentException("Modalidade do Diário de Classe inválida.");
        };
    }

    private String normalizeAttendance(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return switch (normalized) {
            case "PRESENT", "ABSENT", "EXCUSED" -> normalized;
            default -> throw new IllegalArgumentException("Situação de frequência inválida.");
        };
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record ClassCore(Long id, Long schoolId, String name) {}

    public record CreateDiaryRequest(@NotNull(message = "Selecione a turma.") Long classId, Long componentId,
                                     @NotBlank(message = "Selecione a modalidade do diário.") String mode,
                                     @NotNull(message = "Selecione o professor responsável.") Long responsibleProfessionalId,
                                     @NotNull(message = "Informe o início da vigência.") LocalDate validFrom, LocalDate validUntil) {}
    public record SaveLessonRequest(String content, String planningNotes, List<AttendanceInput> attendance) {}
    public record AttendanceInput(@NotNull(message = "Informe a matrícula do estudante.") Long enrollmentId, @NotBlank(message = "Informe a frequência.") String status) {}
    public record RosterStudent(Long enrollmentId, Long studentId, String registration, String name) {}
    public record AttendanceView(Long enrollmentId, Long studentId, String studentName, String status) {}
    public record LessonView(Long id, Long diaryId, LocalDate lessonDate, Integer lessonSlot, String content, String planningNotes, String createdBy, String updatedBy, List<AttendanceView> attendance) {}
    public record DiaryView(Long id, Long classId, String className, Long schoolId, String schoolName, Long componentId, String componentName, String mode,
                            Long responsibleProfessionalId, String responsibleProfessionalName, LocalDate validFrom, LocalDate validUntil, boolean active, boolean editable) {}
}
