package br.com.krino.secretaria;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;

@Service
public class AcademicStructureService {

    private final JdbcTemplate jdbcTemplate;
    private final SchoolAccessService accessService;
    private final SecretariaRegistryService registryService;
    private final SecurityAuditService auditService;

    public AcademicStructureService(JdbcTemplate jdbcTemplate, SchoolAccessService accessService, SecretariaRegistryService registryService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.registryService = registryService;
        this.auditService = auditService;
    }

    public List<TeacherAssignmentView> assignments(long classId, Authentication authentication) {
        var classView = registryService.getClass(classId);
        accessService.requireRead(authentication, classView.schoolId());
        return jdbcTemplate.query("select a.id, a.professional_id, p.name professional_name, a.class_id, a.component_id, cc.name component_name, a.valid_from, a.valid_until from teacher_assignment a join education_professional p on p.id = a.professional_id join curricular_component cc on cc.id = a.component_id where a.class_id = ? order by cc.name, a.valid_from",
                this::mapAssignment, classId);
    }

    @Transactional
    public TeacherAssignmentView assign(TeacherAssignmentRequest request, Authentication authentication) {
        var classView = registryService.getClass(request.classId());
        accessService.requireWrite(authentication, classView.schoolId());
        var professional = registryService.getProfessional(request.professionalId());
        if (!professional.active()) throw new IllegalArgumentException("O profissional selecionado está inativo.");
        if (request.validUntil() != null && request.validUntil().isBefore(request.validFrom())) throw new IllegalArgumentException("A data final do vínculo não pode ser anterior à data inicial.");

        String overlapSql = "select count(*) from teacher_assignment where professional_id = ? and class_id = ? and component_id = ? and (valid_until is null or valid_until >= ?)";
        Integer overlap = request.validUntil() == null
                ? jdbcTemplate.queryForObject(overlapSql, Integer.class, request.professionalId(), request.classId(), request.componentId(), Date.valueOf(request.validFrom()))
                : jdbcTemplate.queryForObject(overlapSql + " and valid_from <= ?", Integer.class, request.professionalId(), request.classId(), request.componentId(), Date.valueOf(request.validFrom()), Date.valueOf(request.validUntil()));
        if (overlap != null && overlap > 0) throw new IllegalArgumentException("Já existe atribuição deste profissional ao componente na turma com vigência sobreposta.");

        long id = insertReturningId("insert into teacher_assignment (professional_id, class_id, component_id, valid_from, valid_until) values (?, ?, ?, ?, ?)", request.professionalId(), request.classId(), request.componentId(), request.validFrom(), request.validUntil());
        auditService.record(authentication.getName(), "TEACHER_ASSIGNED", "TEACHER_ASSIGNMENT", Long.toString(id), professional.name() + " / " + classView.name());
        return getAssignment(id);
    }

    @Transactional
    public void removeAssignment(long id, Authentication authentication) {
        TeacherAssignmentView view = getAssignment(id);
        var classView = registryService.getClass(view.classId());
        accessService.requireWrite(authentication, classView.schoolId());
        jdbcTemplate.update("delete from teacher_assignment where id = ?", id);
        auditService.record(authentication.getName(), "TEACHER_ASSIGNMENT_REMOVED", "TEACHER_ASSIGNMENT", Long.toString(id), view.professionalName());
    }

    public List<CalendarDayView> calendar(long schoolId, int year, Authentication authentication) {
        accessService.requireRead(authentication, schoolId);
        return jdbcTemplate.query("select id, school_id, academic_date, school_day, description from school_calendar_day where school_id = ? and extract(year from academic_date) = ? order by academic_date",
                (rs, rowNum) -> new CalendarDayView(rs.getLong("id"), rs.getLong("school_id"), rs.getDate("academic_date").toLocalDate(), rs.getBoolean("school_day"), rs.getString("description")), schoolId, year);
    }

    @Transactional
    public CalendarDayView saveCalendarDay(CalendarDayRequest request, Authentication authentication) {
        accessService.requireWrite(authentication, request.schoolId());
        jdbcTemplate.update("insert into school_calendar_day (school_id, academic_date, school_day, description) values (?, ?, ?, ?) on conflict (school_id, academic_date) do update set school_day = excluded.school_day, description = excluded.description, updated_at = current_timestamp",
                request.schoolId(), Date.valueOf(request.academicDate()), request.schoolDay(), request.description());
        Long id = jdbcTemplate.queryForObject("select id from school_calendar_day where school_id = ? and academic_date = ?", Long.class, request.schoolId(), Date.valueOf(request.academicDate()));
        auditService.record(authentication.getName(), "CALENDAR_DAY_SAVED", "SCHOOL_CALENDAR_DAY", Long.toString(id), request.academicDate().toString());
        return new CalendarDayView(id, request.schoolId(), request.academicDate(), request.schoolDay(), request.description());
    }

    public List<ScheduleView> schedules(long classId, LocalDate referenceDate, Authentication authentication) {
        var classView = registryService.getClass(classId);
        accessService.requireRead(authentication, classView.schoolId());
        LocalDate date = referenceDate == null ? LocalDate.now() : referenceDate;
        return jdbcTemplate.query("select s.id, s.class_id, s.component_id, cc.name component_name, s.professional_id, p.name professional_name, s.day_of_week, s.start_time, s.end_time, s.valid_from, s.valid_until from class_schedule s join curricular_component cc on cc.id = s.component_id left join education_professional p on p.id = s.professional_id where s.class_id = ? and s.valid_from <= ? and (s.valid_until is null or s.valid_until >= ?) order by s.day_of_week, s.start_time",
                this::mapSchedule, classId, Date.valueOf(date), Date.valueOf(date));
    }

    @Transactional
    public ScheduleView createSchedule(ScheduleRequest request, Authentication authentication) {
        var classView = registryService.getClass(request.classId());
        accessService.requireWrite(authentication, classView.schoolId());
        if (!request.endTime().isAfter(request.startTime())) throw new IllegalArgumentException("O horário final deve ser posterior ao horário inicial.");
        if (request.validUntil() != null && request.validUntil().isBefore(request.validFrom())) throw new IllegalArgumentException("A vigência final do horário não pode ser anterior à inicial.");
        if (request.professionalId() != null) {
            Integer assignment = jdbcTemplate.queryForObject("select count(*) from teacher_assignment where professional_id = ? and class_id = ? and component_id = ? and valid_from <= ? and (valid_until is null or valid_until >= ?)", Integer.class, request.professionalId(), request.classId(), request.componentId(), Date.valueOf(request.validFrom()), Date.valueOf(request.validFrom()));
            if (assignment == null || assignment == 0) throw new IllegalArgumentException("O professor precisa estar atribuído à turma e ao componente no período informado.");
        }

        String validityCondition = " and (valid_until is null or valid_until >= ?)" + (request.validUntil() == null ? "" : " and valid_from <= ?");
        String conflictSql = "select count(*) from class_schedule where day_of_week = ? and start_time < ? and end_time > ?" + validityCondition + " and class_id = ?";
        Object[] classArgs = request.validUntil() == null
                ? new Object[]{request.dayOfWeek(), Time.valueOf(request.endTime()), Time.valueOf(request.startTime()), Date.valueOf(request.validFrom()), request.classId()}
                : new Object[]{request.dayOfWeek(), Time.valueOf(request.endTime()), Time.valueOf(request.startTime()), Date.valueOf(request.validFrom()), Date.valueOf(request.validUntil()), request.classId()};
        Integer classConflict = jdbcTemplate.queryForObject(conflictSql, Integer.class, classArgs);
        if (classConflict != null && classConflict > 0) throw new IllegalArgumentException("Já existe horário da turma sobreposto no mesmo dia e período de vigência.");

        if (request.professionalId() != null) {
            String professionalSql = "select count(*) from class_schedule where professional_id = ? and day_of_week = ? and start_time < ? and end_time > ?" + validityCondition;
            Object[] professionalArgs = request.validUntil() == null
                    ? new Object[]{request.professionalId(), request.dayOfWeek(), Time.valueOf(request.endTime()), Time.valueOf(request.startTime()), Date.valueOf(request.validFrom())}
                    : new Object[]{request.professionalId(), request.dayOfWeek(), Time.valueOf(request.endTime()), Time.valueOf(request.startTime()), Date.valueOf(request.validFrom()), Date.valueOf(request.validUntil())};
            Integer professionalConflict = jdbcTemplate.queryForObject(professionalSql, Integer.class, professionalArgs);
            if (professionalConflict != null && professionalConflict > 0) throw new IllegalArgumentException("O professor já possui outro horário sobreposto neste dia e período de vigência.");
        }

        long id = insertReturningId("insert into class_schedule (class_id, component_id, professional_id, day_of_week, start_time, end_time, valid_from, valid_until) values (?, ?, ?, ?, ?, ?, ?, ?)", request.classId(), request.componentId(), request.professionalId(), request.dayOfWeek(), request.startTime(), request.endTime(), request.validFrom(), request.validUntil());
        auditService.record(authentication.getName(), "CLASS_SCHEDULE_CREATED", "CLASS_SCHEDULE", Long.toString(id), classView.name());
        return getSchedule(id);
    }

    @Transactional
    public void deleteSchedule(long id, Authentication authentication) {
        ScheduleView schedule = getSchedule(id);
        var classView = registryService.getClass(schedule.classId());
        accessService.requireWrite(authentication, classView.schoolId());
        jdbcTemplate.update("delete from class_schedule where id = ?", id);
        auditService.record(authentication.getName(), "CLASS_SCHEDULE_DELETED", "CLASS_SCHEDULE", Long.toString(id), classView.name());
    }

    private TeacherAssignmentView getAssignment(long id) {
        var rows = jdbcTemplate.query("select a.id, a.professional_id, p.name professional_name, a.class_id, a.component_id, cc.name component_name, a.valid_from, a.valid_until from teacher_assignment a join education_professional p on p.id = a.professional_id join curricular_component cc on cc.id = a.component_id where a.id = ?", this::mapAssignment, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Atribuição de professor não encontrada.");
        return rows.getFirst();
    }

    private ScheduleView getSchedule(long id) {
        var rows = jdbcTemplate.query("select s.id, s.class_id, s.component_id, cc.name component_name, s.professional_id, p.name professional_name, s.day_of_week, s.start_time, s.end_time, s.valid_from, s.valid_until from class_schedule s join curricular_component cc on cc.id = s.component_id left join education_professional p on p.id = s.professional_id where s.id = ?", this::mapSchedule, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Horário de aula não encontrado.");
        return rows.getFirst();
    }

    private TeacherAssignmentView mapAssignment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Date until = rs.getDate("valid_until");
        return new TeacherAssignmentView(rs.getLong("id"), rs.getLong("professional_id"), rs.getString("professional_name"), rs.getLong("class_id"), rs.getLong("component_id"), rs.getString("component_name"), rs.getDate("valid_from").toLocalDate(), until == null ? null : until.toLocalDate());
    }

    private ScheduleView mapSchedule(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        long professionalId = rs.getLong("professional_id");
        boolean professionalIdWasNull = rs.wasNull();
        Date until = rs.getDate("valid_until");
        return new ScheduleView(rs.getLong("id"), rs.getLong("class_id"), rs.getLong("component_id"), rs.getString("component_name"), professionalIdWasNull ? null : professionalId, rs.getString("professional_name"), rs.getInt("day_of_week"), rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(), rs.getDate("valid_from").toLocalDate(), until == null ? null : until.toLocalDate());
    }

    private long insertReturningId(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < values.length; index++) {
                Object value = values[index];
                if (value instanceof LocalDate localDate) statement.setDate(index + 1, Date.valueOf(localDate));
                else if (value instanceof LocalTime localTime) statement.setTime(index + 1, Time.valueOf(localTime));
                else statement.setObject(index + 1, value);
            }
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public record TeacherAssignmentRequest(@NotNull(message = "Selecione o profissional da educação.") Long professionalId, @NotNull(message = "Selecione a turma.") Long classId, @NotNull(message = "Selecione o componente curricular.") Long componentId, @NotNull(message = "Informe o início da vigência.") LocalDate validFrom, LocalDate validUntil) {}
    public record CalendarDayRequest(@NotNull(message = "Selecione a unidade escolar.") Long schoolId, @NotNull(message = "Informe a data do calendário.") LocalDate academicDate, boolean schoolDay, String description) {}
    public record ScheduleRequest(@NotNull(message = "Selecione a turma.") Long classId, @NotNull(message = "Selecione o componente curricular.") Long componentId, Long professionalId, @NotNull @Min(1) @Max(7) Integer dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime, @NotNull LocalDate validFrom, LocalDate validUntil) {}

    public record TeacherAssignmentView(Long id, Long professionalId, String professionalName, Long classId, Long componentId, String componentName, LocalDate validFrom, LocalDate validUntil) {}
    public record CalendarDayView(Long id, Long schoolId, LocalDate academicDate, boolean schoolDay, String description) {}
    public record ScheduleView(Long id, Long classId, Long componentId, String componentName, Long professionalId, String professionalName, Integer dayOfWeek, LocalTime startTime, LocalTime endTime, LocalDate validFrom, LocalDate validUntil) {}
}
