package br.com.krino.family;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;

@Service
public class FamilyPortalService {

    private final JdbcTemplate jdbcTemplate;
    private final FamilyPortalAccessService accessService;
    private final SecurityAuditService auditService;

    public FamilyPortalService(JdbcTemplate jdbcTemplate, FamilyPortalAccessService accessService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    public List<LinkedStudentView> students(Authentication authentication) {
        accessService.requirePortalPermission(authentication);
        long userId = accessService.userId(authentication);
        return jdbcTemplate.query(
                "select st.id, st.registration, st.name, e.academic_year, c.id class_id, c.name class_name, s.id school_id, s.name school_name "
                        + "from linked_resource_access lra join student st on st.id::text = lra.resource_reference "
                        + "left join lateral (select se.academic_year, se.class_id from student_enrollment se where se.student_id = st.id and se.status = 'ACTIVE' "
                        + "order by se.academic_year desc, se.enrollment_date desc, se.id desc limit 1) e on true "
                        + "left join school_class c on c.id = e.class_id left join school_unit s on s.id = c.school_id "
                        + "where lra.user_id = ? and lra.resource_type = 'STUDENT' and lra.access_level in ('READ', 'EDIT') and st.status = 'ACTIVE' order by st.name",
                (rs, rowNum) -> new LinkedStudentView(rs.getLong("id"), rs.getString("registration"), rs.getString("name"),
                        nullableInteger(rs, "academic_year"), nullableLong(rs, "class_id"), rs.getString("class_name"), nullableLong(rs, "school_id"), rs.getString("school_name")), userId);
    }

    public ReportCardView reportCard(long studentId, int academicYear, int period, Authentication authentication) {
        accessService.requireLinkedStudent(studentId, authentication);
        validateAcademicYear(academicYear);
        validatePeriod(period);
        List<ComponentResultView> components = jdbcTemplate.query(
                "select cc.id component_id, cc.name component_name, r.grade, r.absences, r.classes_count "
                        + "from student_term_result r join student_enrollment e on e.id = r.enrollment_id "
                        + "join curricular_component cc on cc.id = r.component_id "
                        + "where e.student_id = ? and e.academic_year = ? and r.period = ? order by cc.name",
                (rs, rowNum) -> componentResult(rs.getLong("component_id"), rs.getString("component_name"), rs.getBigDecimal("grade"), rs.getInt("absences"), rs.getInt("classes_count")),
                studentId, academicYear, period);
        List<AssessmentView> assessments = jdbcTemplate.query(
                "select coalesce(cc.name, 'Avaliação geral') component_name, a.title, a.assessment_date, g.score, a.max_score, g.observation "
                        + "from diary_assessment_grade g join student_enrollment e on e.id = g.enrollment_id "
                        + "join diary_assessment a on a.id = g.assessment_id join class_diary d on d.id = a.diary_id "
                        + "left join curricular_component cc on cc.id = d.component_id "
                        + "where e.student_id = ? and e.academic_year = ? and a.period = ? order by a.assessment_date, cc.name, a.title",
                (rs, rowNum) -> new AssessmentView(rs.getString("component_name"), rs.getString("title"), rs.getObject("assessment_date", java.time.LocalDate.class),
                        rs.getBigDecimal("score"), rs.getBigDecimal("max_score"), rs.getString("observation")), studentId, academicYear, period);
        int totalClasses = components.stream().mapToInt(ComponentResultView::classesCount).sum();
        int totalAbsences = components.stream().mapToInt(ComponentResultView::absences).sum();
        return new ReportCardView(studentId, academicYear, period, components, assessments, totalClasses, totalAbsences,
                FamilyAttendanceCalculator.percentage(totalClasses, totalAbsences));
    }

    public List<AccessNotificationView> notifications(long studentId, Authentication authentication) {
        accessService.requireLinkedStudent(studentId, authentication);
        return jdbcTemplate.query(
                "select n.id, n.message, n.available_at, e.event_type, e.captured_at "
                        + "from student_access_notification n join student_access_event e on e.id = n.event_id "
                        + "where n.student_id = ? order by n.available_at desc, n.id desc limit 100",
                (rs, rowNum) -> new AccessNotificationView(rs.getLong("id"), rs.getString("event_type"),
                        rs.getObject("captured_at", OffsetDateTime.class), rs.getString("message"), rs.getObject("available_at", OffsetDateTime.class)), studentId);
    }

    public List<AnnouncementView> announcements(long studentId, Authentication authentication) {
        accessService.requireLinkedStudent(studentId, authentication);
        return jdbcTemplate.query(
                "select a.id, a.audience_type, a.title, a.body, a.published_at, s.name school_name "
                        + "from student st join lateral (select se.class_id from student_enrollment se where se.student_id = st.id and se.status = 'ACTIVE' "
                        + "order by se.academic_year desc, se.enrollment_date desc, se.id desc limit 1) e on true "
                        + "join school_class c on c.id = e.class_id join school_unit s on s.id = c.school_id "
                        + "join family_announcement a on a.school_id = c.school_id and a.active = true "
                        + "and (a.audience_type = 'SCHOOL' or (a.audience_type = 'CLASS' and a.class_id = c.id) or (a.audience_type = 'STUDENT' and a.student_id = st.id)) "
                        + "where st.id = ? order by a.published_at desc, a.id desc",
                (rs, rowNum) -> new AnnouncementView(rs.getLong("id"), rs.getString("audience_type"), rs.getString("title"), rs.getString("body"),
                        rs.getString("school_name"), rs.getObject("published_at", OffsetDateTime.class)), studentId);
    }

    public List<ConversationView> conversations(long studentId, Authentication authentication) {
        accessService.requireLinkedStudent(studentId, authentication);
        long userId = accessService.userId(authentication);
        return jdbcTemplate.query(
                "select c.id, c.subject, c.status, c.updated_at, s.name school_name, "
                        + "(select m.body from family_message m where m.conversation_id = c.id order by m.created_at desc, m.id desc limit 1) last_message "
                        + "from family_conversation c join school_unit s on s.id = c.school_id "
                        + "where c.guardian_user_id = ? and c.student_id = ? order by c.updated_at desc",
                (rs, rowNum) -> new ConversationView(rs.getLong("id"), rs.getString("subject"), rs.getString("status"), rs.getString("school_name"),
                        rs.getString("last_message"), rs.getObject("updated_at", OffsetDateTime.class)), userId, studentId);
    }

    public List<MessageView> messages(long conversationId, Authentication authentication) {
        GuardianConversation conversation = guardianConversation(conversationId, authentication);
        accessService.requireLinkedStudent(conversation.studentId(), authentication);
        return jdbcTemplate.query(
                "select m.id, m.sender_type, u.display_name sender_name, m.body, m.created_at from family_message m "
                        + "join app_user u on u.id = m.sender_user_id where m.conversation_id = ? order by m.created_at, m.id",
                (rs, rowNum) -> new MessageView(rs.getLong("id"), rs.getString("sender_type"), rs.getString("sender_name"), rs.getString("body"),
                        rs.getObject("created_at", OffsetDateTime.class)), conversationId);
    }

    @Transactional
    public ConversationView createConversation(NewConversationRequest request, Authentication authentication) {
        accessService.requireLinkedStudent(request.studentId(), authentication);
        long userId = accessService.userId(authentication);
        CurrentEnrollment enrollment = currentEnrollment(request.studentId());
        Long id = jdbcTemplate.queryForObject(
                "insert into family_conversation (student_id, school_id, guardian_user_id, subject) values (?, ?, ?, ?) returning id",
                Long.class, request.studentId(), enrollment.schoolId(), userId, request.subject().trim());
        jdbcTemplate.update("insert into family_message (conversation_id, sender_user_id, sender_type, body) values (?, ?, 'GUARDIAN', ?)",
                id, userId, request.message().trim());
        auditService.record(authentication.getName(), "FAMILY_CONVERSATION_CREATED", "FAMILY_CONVERSATION", Long.toString(id), "Conversa iniciada pelo responsável para estudante " + request.studentId());
        return conversation(id, userId);
    }

    @Transactional
    public void reply(long conversationId, MessageRequest request, Authentication authentication) {
        GuardianConversation conversation = guardianConversation(conversationId, authentication);
        accessService.requireLinkedStudent(conversation.studentId(), authentication);
        if (conversation.closed()) throw new IllegalArgumentException("Esta conversa está encerrada e não aceita novas mensagens.");
        long userId = accessService.userId(authentication);
        jdbcTemplate.update("insert into family_message (conversation_id, sender_user_id, sender_type, body) values (?, ?, 'GUARDIAN', ?)",
                conversationId, userId, request.message().trim());
        jdbcTemplate.update("update family_conversation set updated_at = current_timestamp where id = ?", conversationId);
        auditService.record(authentication.getName(), "FAMILY_MESSAGE_SENT", "FAMILY_CONVERSATION", Long.toString(conversationId), "Mensagem enviada pelo responsável.");
    }

    private GuardianConversation guardianConversation(long conversationId, Authentication authentication) {
        long userId = accessService.userId(authentication);
        List<GuardianConversation> rows = jdbcTemplate.query(
                "select id, student_id, status from family_conversation where id = ? and guardian_user_id = ?",
                (rs, rowNum) -> new GuardianConversation(rs.getLong("id"), rs.getLong("student_id"), "CLOSED".equals(rs.getString("status"))), conversationId, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Conversa não encontrada para esta conta.");
        return rows.getFirst();
    }

    private ConversationView conversation(long id, long userId) {
        return jdbcTemplate.queryForObject(
                "select c.id, c.subject, c.status, c.updated_at, s.name school_name, "
                        + "(select m.body from family_message m where m.conversation_id = c.id order by m.created_at desc, m.id desc limit 1) last_message "
                        + "from family_conversation c join school_unit s on s.id = c.school_id where c.id = ? and c.guardian_user_id = ?",
                (rs, rowNum) -> new ConversationView(rs.getLong("id"), rs.getString("subject"), rs.getString("status"), rs.getString("school_name"),
                        rs.getString("last_message"), rs.getObject("updated_at", OffsetDateTime.class)), id, userId);
    }

    private CurrentEnrollment currentEnrollment(long studentId) {
        List<CurrentEnrollment> rows = jdbcTemplate.query(
                "select e.class_id, c.school_id from student_enrollment e join school_class c on c.id = e.class_id where e.student_id = ? and e.status = 'ACTIVE' order by e.academic_year desc, e.enrollment_date desc, e.id desc limit 1",
                (rs, rowNum) -> new CurrentEnrollment(rs.getLong("class_id"), rs.getLong("school_id")), studentId);
        if (rows.isEmpty()) throw new IllegalArgumentException("O estudante não possui matrícula ativa para iniciar comunicação com a escola.");
        return rows.getFirst();
    }

    private ComponentResultView componentResult(long id, String name, BigDecimal grade, int absences, int classesCount) {
        return new ComponentResultView(id, name, grade, absences, classesCount, FamilyAttendanceCalculator.percentage(classesCount, absences));
    }

    private void validateAcademicYear(int academicYear) {
        if (academicYear < 2000 || academicYear > 2200) throw new IllegalArgumentException("Informe um ano letivo válido.");
    }

    private void validatePeriod(int period) {
        if (period < 1 || period > 4) throw new IllegalArgumentException("O período deve estar entre 1 e 4.");
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record CurrentEnrollment(Long classId, Long schoolId) {}
    private record GuardianConversation(Long id, Long studentId, boolean closed) {}

    public record LinkedStudentView(Long id, String registration, String name, Integer academicYear, Long classId, String className, Long schoolId, String schoolName) {}
    public record ComponentResultView(Long componentId, String componentName, BigDecimal grade, int absences, int classesCount, BigDecimal attendancePercent) {}
    public record AssessmentView(String componentName, String title, java.time.LocalDate assessmentDate, BigDecimal score, BigDecimal maxScore, String observation) {}
    public record ReportCardView(Long studentId, Integer academicYear, Integer period, List<ComponentResultView> components, List<AssessmentView> assessments,
                                 int totalClasses, int totalAbsences, BigDecimal attendancePercent) {}
    public record AccessNotificationView(Long id, String eventType, OffsetDateTime capturedAt, String message, OffsetDateTime availableAt) {}
    public record AnnouncementView(Long id, String audienceType, String title, String body, String schoolName, OffsetDateTime publishedAt) {}
    public record ConversationView(Long id, String subject, String status, String schoolName, String lastMessage, OffsetDateTime updatedAt) {}
    public record MessageView(Long id, String senderType, String senderName, String body, OffsetDateTime createdAt) {}
    public record NewConversationRequest(@NotNull(message = "Selecione o estudante.") Long studentId,
                                         @NotBlank(message = "Informe o assunto.") @Size(max = 180, message = "O assunto deve ter no máximo 180 caracteres.") String subject,
                                         @NotBlank(message = "Escreva a mensagem.") @Size(max = 4000, message = "A mensagem deve ter no máximo 4000 caracteres.") String message) {}
    public record MessageRequest(@NotBlank(message = "Escreva a mensagem.") @Size(max = 4000, message = "A mensagem deve ter no máximo 4000 caracteres.") String message) {}
}
