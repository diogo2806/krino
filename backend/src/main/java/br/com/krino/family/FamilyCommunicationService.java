package br.com.krino.family;

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
public class FamilyCommunicationService {

    private final JdbcTemplate jdbcTemplate;
    private final FamilyPortalAccessService accessService;
    private final SecurityAuditService auditService;

    public FamilyCommunicationService(JdbcTemplate jdbcTemplate, FamilyPortalAccessService accessService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    public List<SchoolOption> schools(Authentication authentication) {
        List<Long> ids = accessService.accessibleSchools(authentication);
        if (ids.isEmpty()) return List.of();
        return jdbcTemplate.query("select id, code, name from school_unit where id = any(?) order by name",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray())),
                (rs, rowNum) -> new SchoolOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
    }

    public List<ClassOption> classes(long schoolId, Authentication authentication) {
        accessService.requireSchoolRead(schoolId, authentication);
        return jdbcTemplate.query("select id, name, academic_year from school_class where school_id = ? and active = true order by academic_year desc, name",
                (rs, rowNum) -> new ClassOption(rs.getLong("id"), rs.getString("name"), rs.getInt("academic_year")), schoolId);
    }

    public List<StudentOption> students(long schoolId, Long classId, Authentication authentication) {
        accessService.requireSchoolRead(schoolId, authentication);
        String sql = "select distinct st.id, st.registration, st.name, c.id class_id, c.name class_name from student st "
                + "join student_enrollment e on e.student_id = st.id and e.status = 'ACTIVE' join school_class c on c.id = e.class_id "
                + "where c.school_id = ? and st.status = 'ACTIVE'" + (classId == null ? "" : " and c.id = ?") + " order by st.name";
        return classId == null
                ? jdbcTemplate.query(sql, (rs, rowNum) -> studentOption(rs), schoolId)
                : jdbcTemplate.query(sql, (rs, rowNum) -> studentOption(rs), schoolId, classId);
    }

    public List<GuardianOption> guardians(long studentId, Authentication authentication) {
        StudentSchool student = currentStudentSchool(studentId);
        accessService.requireSchoolRead(student.schoolId(), authentication);
        return jdbcTemplate.query(
                "select distinct u.id, u.display_name, u.username from linked_resource_access lra join app_user u on u.id = lra.user_id "
                        + "where lra.resource_type = 'STUDENT' and lra.resource_reference = ? and lra.access_level in ('READ', 'EDIT') and u.active = true "
                        + "and exists (select 1 from user_role_assignment ura join access_role_permission rp on rp.role_id = ura.role_id "
                        + "join access_permission p on p.id = rp.permission_id where ura.user_id = u.id and p.code = 'STUDENT_LINKED_READ') order by u.display_name",
                (rs, rowNum) -> new GuardianOption(rs.getLong("id"), rs.getString("display_name"), rs.getString("username")), Long.toString(studentId));
    }

    public List<ConversationView> conversations(long schoolId, String search, Authentication authentication) {
        accessService.requireSchoolRead(schoolId, authentication);
        String term = search == null ? "" : search.trim();
        return jdbcTemplate.query(
                "select c.id, c.student_id, st.name student_name, u.display_name guardian_name, c.subject, c.status, c.updated_at, "
                        + "(select m.body from family_message m where m.conversation_id = c.id order by m.created_at desc, m.id desc limit 1) last_message "
                        + "from family_conversation c join student st on st.id = c.student_id join app_user u on u.id = c.guardian_user_id "
                        + "where c.school_id = ? and (? = '' or lower(st.name) like lower(?) or lower(u.display_name) like lower(?) or lower(c.subject) like lower(?)) "
                        + "order by c.updated_at desc",
                (rs, rowNum) -> new ConversationView(rs.getLong("id"), rs.getLong("student_id"), rs.getString("student_name"), rs.getString("guardian_name"),
                        rs.getString("subject"), rs.getString("status"), rs.getString("last_message"), rs.getObject("updated_at", OffsetDateTime.class)),
                schoolId, term, "%" + term + "%", "%" + term + "%", "%" + term + "%");
    }

    public List<MessageView> messages(long conversationId, Authentication authentication) {
        ConversationScope scope = conversationScope(conversationId);
        accessService.requireSchoolRead(scope.schoolId(), authentication);
        return jdbcTemplate.query(
                "select m.id, m.sender_type, u.display_name sender_name, m.body, m.created_at from family_message m "
                        + "join app_user u on u.id = m.sender_user_id where m.conversation_id = ? order by m.created_at, m.id",
                (rs, rowNum) -> new MessageView(rs.getLong("id"), rs.getString("sender_type"), rs.getString("sender_name"), rs.getString("body"),
                        rs.getObject("created_at", OffsetDateTime.class)), conversationId);
    }

    @Transactional
    public ConversationView createConversation(NewStaffConversationRequest request, Authentication authentication) {
        StudentSchool student = currentStudentSchool(request.studentId());
        accessService.requireSchoolWrite(student.schoolId(), authentication);
        requireGuardianLink(request.guardianUserId(), request.studentId());
        long senderId = accessService.userId(authentication);
        Long id = jdbcTemplate.queryForObject(
                "insert into family_conversation (student_id, school_id, guardian_user_id, subject) values (?, ?, ?, ?) returning id",
                Long.class, request.studentId(), student.schoolId(), request.guardianUserId(), request.subject().trim());
        jdbcTemplate.update("insert into family_message (conversation_id, sender_user_id, sender_type, body) values (?, ?, 'STAFF', ?)",
                id, senderId, request.message().trim());
        auditService.record(authentication.getName(), "FAMILY_CONVERSATION_CREATED_BY_STAFF", "FAMILY_CONVERSATION", Long.toString(id), "Conversa iniciada pela escola.");
        return conversation(id);
    }

    @Transactional
    public void reply(long conversationId, MessageRequest request, Authentication authentication) {
        ConversationScope scope = conversationScope(conversationId);
        accessService.requireSchoolWrite(scope.schoolId(), authentication);
        if (scope.closed()) throw new IllegalArgumentException("Esta conversa está encerrada e não aceita novas mensagens.");
        jdbcTemplate.update("insert into family_message (conversation_id, sender_user_id, sender_type, body) values (?, ?, 'STAFF', ?)",
                conversationId, accessService.userId(authentication), request.message().trim());
        jdbcTemplate.update("update family_conversation set updated_at = current_timestamp where id = ?", conversationId);
        auditService.record(authentication.getName(), "FAMILY_MESSAGE_SENT_BY_STAFF", "FAMILY_CONVERSATION", Long.toString(conversationId), "Mensagem enviada pela escola.");
    }

    public List<AnnouncementView> announcements(long schoolId, Authentication authentication) {
        accessService.requireSchoolRead(schoolId, authentication);
        return jdbcTemplate.query(
                "select a.id, a.audience_type, a.title, a.body, a.published_at, a.active, c.name class_name, st.name student_name "
                        + "from family_announcement a left join school_class c on c.id = a.class_id left join student st on st.id = a.student_id "
                        + "where a.school_id = ? order by a.published_at desc, a.id desc",
                (rs, rowNum) -> new AnnouncementView(rs.getLong("id"), rs.getString("audience_type"), rs.getString("title"), rs.getString("body"),
                        rs.getString("class_name"), rs.getString("student_name"), rs.getBoolean("active"), rs.getObject("published_at", OffsetDateTime.class)), schoolId);
    }

    @Transactional
    public AnnouncementView createAnnouncement(AnnouncementRequest request, Authentication authentication) {
        String audience = normalizeAudience(request.audienceType());
        accessService.requireSchoolWrite(request.schoolId(), authentication);
        Long classId = null;
        Long studentId = null;
        if (audience.equals("CLASS")) {
            if (request.classId() == null) throw new IllegalArgumentException("Selecione a turma do comunicado.");
            requireClassSchool(request.classId(), request.schoolId());
            classId = request.classId();
        } else if (audience.equals("STUDENT")) {
            if (request.studentId() == null) throw new IllegalArgumentException("Selecione o estudante do comunicado.");
            StudentSchool student = currentStudentSchool(request.studentId());
            if (!student.schoolId().equals(request.schoolId())) throw new IllegalArgumentException("O estudante selecionado não pertence à unidade escolar informada.");
            studentId = request.studentId();
        }
        Long id = jdbcTemplate.queryForObject(
                "insert into family_announcement (school_id, class_id, student_id, audience_type, title, body, published_by) values (?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, request.schoolId(), classId, studentId, audience, request.title().trim(), request.body().trim(), authentication.getName());
        auditService.record(authentication.getName(), "FAMILY_ANNOUNCEMENT_PUBLISHED", "FAMILY_ANNOUNCEMENT", Long.toString(id), audience + " / escola " + request.schoolId());
        return announcement(id);
    }

    @Transactional
    public void deactivateAnnouncement(long id, Authentication authentication) {
        AnnouncementScope scope = announcementScope(id);
        accessService.requireSchoolWrite(scope.schoolId(), authentication);
        jdbcTemplate.update("update family_announcement set active = false where id = ?", id);
        auditService.record(authentication.getName(), "FAMILY_ANNOUNCEMENT_DEACTIVATED", "FAMILY_ANNOUNCEMENT", Long.toString(id), "Comunicado desativado.");
    }

    private ConversationView conversation(long id) {
        return jdbcTemplate.queryForObject(
                "select c.id, c.student_id, st.name student_name, u.display_name guardian_name, c.subject, c.status, c.updated_at, "
                        + "(select m.body from family_message m where m.conversation_id = c.id order by m.created_at desc, m.id desc limit 1) last_message "
                        + "from family_conversation c join student st on st.id = c.student_id join app_user u on u.id = c.guardian_user_id where c.id = ?",
                (rs, rowNum) -> new ConversationView(rs.getLong("id"), rs.getLong("student_id"), rs.getString("student_name"), rs.getString("guardian_name"),
                        rs.getString("subject"), rs.getString("status"), rs.getString("last_message"), rs.getObject("updated_at", OffsetDateTime.class)), id);
    }

    private AnnouncementView announcement(long id) {
        return jdbcTemplate.queryForObject(
                "select a.id, a.audience_type, a.title, a.body, a.published_at, a.active, c.name class_name, st.name student_name "
                        + "from family_announcement a left join school_class c on c.id = a.class_id left join student st on st.id = a.student_id where a.id = ?",
                (rs, rowNum) -> new AnnouncementView(rs.getLong("id"), rs.getString("audience_type"), rs.getString("title"), rs.getString("body"),
                        rs.getString("class_name"), rs.getString("student_name"), rs.getBoolean("active"), rs.getObject("published_at", OffsetDateTime.class)), id);
    }

    private StudentOption studentOption(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StudentOption(rs.getLong("id"), rs.getString("registration"), rs.getString("name"), rs.getLong("class_id"), rs.getString("class_name"));
    }

    private StudentSchool currentStudentSchool(long studentId) {
        List<StudentSchool> rows = jdbcTemplate.query(
                "select e.class_id, c.school_id from student_enrollment e join school_class c on c.id = e.class_id where e.student_id = ? and e.status = 'ACTIVE' order by e.academic_year desc, e.enrollment_date desc limit 1",
                (rs, rowNum) -> new StudentSchool(rs.getLong("class_id"), rs.getLong("school_id")), studentId);
        if (rows.isEmpty()) throw new IllegalArgumentException("O estudante não possui matrícula ativa.");
        return rows.getFirst();
    }

    private void requireGuardianLink(long guardianUserId, long studentId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from linked_resource_access lra join app_user u on u.id = lra.user_id and u.active = true "
                        + "where lra.user_id = ? and lra.resource_type = 'STUDENT' and lra.resource_reference = ? and lra.access_level in ('READ', 'EDIT') "
                        + "and exists (select 1 from user_role_assignment ura join access_role_permission rp on rp.role_id = ura.role_id "
                        + "join access_permission p on p.id = rp.permission_id where ura.user_id = lra.user_id and p.code = 'STUDENT_LINKED_READ')",
                Integer.class, guardianUserId, Long.toString(studentId));
        if (count == null || count == 0) throw new IllegalArgumentException("O responsável selecionado não possui vínculo autorizado ativo com este estudante.");
    }

    private void requireClassSchool(long classId, long schoolId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from school_class where id = ? and school_id = ? and active = true", Integer.class, classId, schoolId);
        if (count == null || count == 0) throw new IllegalArgumentException("A turma selecionada não pertence à unidade escolar informada.");
    }

    private ConversationScope conversationScope(long id) {
        List<ConversationScope> rows = jdbcTemplate.query("select school_id, status from family_conversation where id = ?",
                (rs, rowNum) -> new ConversationScope(rs.getLong("school_id"), "CLOSED".equals(rs.getString("status"))), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Conversa não encontrada.");
        return rows.getFirst();
    }

    private AnnouncementScope announcementScope(long id) {
        List<AnnouncementScope> rows = jdbcTemplate.query("select school_id from family_announcement where id = ?",
                (rs, rowNum) -> new AnnouncementScope(rs.getLong("school_id")), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Comunicado não encontrado.");
        return rows.getFirst();
    }

    private String normalizeAudience(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!(normalized.equals("SCHOOL") || normalized.equals("CLASS") || normalized.equals("STUDENT"))) {
            throw new IllegalArgumentException("Público do comunicado inválido.");
        }
        return normalized;
    }

    private record StudentSchool(Long classId, Long schoolId) {}
    private record ConversationScope(Long schoolId, boolean closed) {}
    private record AnnouncementScope(Long schoolId) {}

    public record SchoolOption(Long id, String code, String name) {}
    public record ClassOption(Long id, String name, Integer academicYear) {}
    public record StudentOption(Long id, String registration, String name, Long classId, String className) {}
    public record GuardianOption(Long id, String displayName, String username) {}
    public record ConversationView(Long id, Long studentId, String studentName, String guardianName, String subject, String status, String lastMessage, OffsetDateTime updatedAt) {}
    public record MessageView(Long id, String senderType, String senderName, String body, OffsetDateTime createdAt) {}
    public record AnnouncementView(Long id, String audienceType, String title, String body, String className, String studentName, boolean active, OffsetDateTime publishedAt) {}
    public record NewStaffConversationRequest(@NotNull(message = "Selecione o estudante.") Long studentId,
                                              @NotNull(message = "Selecione o responsável.") Long guardianUserId,
                                              @NotBlank(message = "Informe o assunto.") @Size(max = 180) String subject,
                                              @NotBlank(message = "Escreva a mensagem.") @Size(max = 4000) String message) {}
    public record MessageRequest(@NotBlank(message = "Escreva a mensagem.") @Size(max = 4000) String message) {}
    public record AnnouncementRequest(@NotNull(message = "Selecione a unidade escolar.") Long schoolId,
                                      @NotBlank(message = "Selecione o público.") String audienceType,
                                      Long classId, Long studentId,
                                      @NotBlank(message = "Informe o título.") @Size(max = 180) String title,
                                      @NotBlank(message = "Escreva o comunicado.") @Size(max = 4000) String body) {}
}
