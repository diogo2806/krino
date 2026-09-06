package br.com.krino.family;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class FamilyAttendanceService {

    private final JdbcTemplate jdbcTemplate;
    private final FamilyPortalAccessService accessService;

    public FamilyAttendanceService(JdbcTemplate jdbcTemplate, FamilyPortalAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public AttendanceSummary attendance(long studentId, int academicYear, int period, Authentication authentication) {
        accessService.requireLinkedStudent(studentId, authentication);
        validateAcademicYear(academicYear);
        validatePeriod(period);

        List<AttendanceComponent> components = diaryAttendance(studentId, academicYear, period);
        String source = "DIARY";
        if (components.isEmpty()) {
            components = consolidatedAttendance(studentId, academicYear, period);
            source = components.isEmpty() ? "NONE" : "CONSOLIDATED_RESULT";
        }

        int classesCount = components.stream().mapToInt(AttendanceComponent::classesCount).sum();
        int absences = components.stream().mapToInt(AttendanceComponent::absences).sum();
        return new AttendanceSummary(studentId, academicYear, period, source, classesCount, absences,
                FamilyAttendanceCalculator.percentage(classesCount, absences), components);
    }

    private List<AttendanceComponent> diaryAttendance(long studentId, int academicYear, int period) {
        return jdbcTemplate.query(
                "select coalesce(cc.name, 'Frequência geral') component_name, count(*) classes_count, "
                        + "count(*) filter (where a.attendance_status in ('ABSENT', 'EXCUSED')) absences "
                        + "from diary_attendance a join diary_lesson l on l.id = a.lesson_id "
                        + "join class_diary d on d.id = l.diary_id join student_enrollment e on e.id = a.enrollment_id "
                        + "left join curricular_component cc on cc.id = d.component_id "
                        + "where e.student_id = ? and e.academic_year = ? and l.period = ? "
                        + "group by d.component_id, cc.name order by cc.name nulls first",
                (rs, rowNum) -> component(rs.getString("component_name"), rs.getInt("classes_count"), rs.getInt("absences")),
                studentId, academicYear, period);
    }

    private List<AttendanceComponent> consolidatedAttendance(long studentId, int academicYear, int period) {
        return jdbcTemplate.query(
                "select component_name, classes_count, absences from ("
                        + "select distinct on (r.component_id) r.component_id, cc.name component_name, r.classes_count, r.absences, e.enrollment_date, e.id enrollment_id "
                        + "from student_term_result r join student_enrollment e on e.id = r.enrollment_id join curricular_component cc on cc.id = r.component_id "
                        + "where e.student_id = ? and e.academic_year = ? and r.period = ? "
                        + "order by r.component_id, e.enrollment_date desc, e.id desc"
                        + ") current_result order by component_name",
                (rs, rowNum) -> component(rs.getString("component_name"), rs.getInt("classes_count"), rs.getInt("absences")),
                studentId, academicYear, period);
    }

    private AttendanceComponent component(String name, int classesCount, int absences) {
        return new AttendanceComponent(name, classesCount, absences, FamilyAttendanceCalculator.percentage(classesCount, absences));
    }

    private void validateAcademicYear(int academicYear) {
        if (academicYear < 2000 || academicYear > 2200) throw new IllegalArgumentException("Informe um ano letivo válido.");
    }

    private void validatePeriod(int period) {
        if (period < 1 || period > 4) throw new IllegalArgumentException("O período deve estar entre 1 e 4.");
    }

    public record AttendanceComponent(String componentName, int classesCount, int absences, BigDecimal attendancePercent) {}
    public record AttendanceSummary(Long studentId, Integer academicYear, Integer period, String source, int classesCount,
                                    int absences, BigDecimal attendancePercent, List<AttendanceComponent> components) {}
}
