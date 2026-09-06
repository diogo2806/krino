package br.com.krino.accesscontrol;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.accesscontrol.StudentAccessCredentialService.StudentIdentity;

@Service
public class StudentAccessEventService {

    private final JdbcTemplate jdbcTemplate;
    private final StudentAccessCredentialService credentialService;
    private final AccessControlAccessService accessService;
    private final StudentAccessNotificationService notificationService;
    private final SecurityAuditService auditService;

    public StudentAccessEventService(JdbcTemplate jdbcTemplate, StudentAccessCredentialService credentialService,
                                     AccessControlAccessService accessService, StudentAccessNotificationService notificationService,
                                     SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.credentialService = credentialService;
        this.accessService = accessService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public EventView record(EventRequest request, Authentication authentication) {
        validateCapturedAt(request.capturedAt());
        List<StoredEvent> existing = storedEvents(request.clientEventId());
        if (!existing.isEmpty()) {
            StoredEvent stored = existing.getFirst();
            accessService.requireWrite(stored.schoolId(), authentication);
            return eventView(stored.id(), true);
        }

        StudentIdentity identity = credentialService.resolve(request.code());
        accessService.requireWrite(identity.schoolId(), authentication);
        String eventType = normalizeEventType(request.eventType());
        String sourceType = normalizeSourceType(request.sourceType());
        List<Long> inserted = jdbcTemplate.query(
                "insert into student_access_event (client_event_id, student_id, school_id, class_id, event_type, captured_at, captured_offline, source_type, device_id, operator_username) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (client_event_id) do nothing returning id",
                (rs, rowNum) -> rs.getLong("id"), request.clientEventId(), identity.studentId(), identity.schoolId(), identity.classId(), eventType,
                request.capturedAt(), request.capturedOffline(), sourceType, normalizeDeviceId(request.deviceId()), authentication.getName());
        if (inserted.isEmpty()) {
            StoredEvent stored = storedEvents(request.clientEventId()).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Não foi possível recuperar o evento idempotente já registrado."));
            accessService.requireWrite(stored.schoolId(), authentication);
            return eventView(stored.id(), true);
        }

        long eventId = inserted.getFirst();
        notificationService.issue(eventId, identity.studentId(), eventType, request.capturedAt());
        auditService.record(authentication.getName(), "STUDENT_ACCESS_EVENT_RECORDED", "STUDENT_ACCESS_EVENT", Long.toString(eventId),
                eventType + " / estudante " + identity.registration() + (request.capturedOffline() ? " / sincronizado de captura offline" : " / online"));
        return eventView(eventId, false);
    }

    public List<EventView> history(Long schoolId, Long studentId, Integer limit, Authentication authentication) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(200, limit));
        StringBuilder sql = new StringBuilder(
                "select e.id, e.client_event_id, e.event_type, e.captured_at, e.received_at, e.captured_offline, e.source_type, "
                        + "st.id student_id, st.registration, st.name student_name, c.id class_id, c.name class_name, s.id school_id, s.name school_name, "
                        + "exists(select 1 from student_access_notification n where n.event_id = e.id) notification_delivered "
                        + "from student_access_event e join student st on st.id = e.student_id left join school_class c on c.id = e.class_id join school_unit s on s.id = e.school_id where 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (schoolId != null) {
            accessService.requireRead(schoolId, authentication);
            sql.append(" and e.school_id = ?");
            params.add(schoolId);
        } else {
            List<Long> accessible = accessService.accessibleSchoolIds(authentication);
            if (accessible.isEmpty()) return List.of();
            sql.append(" and e.school_id = any(?)");
            params.add(new SqlArrayParameter("bigint", accessible.toArray()));
        }
        if (studentId != null) {
            sql.append(" and e.student_id = ?");
            params.add(studentId);
        }
        sql.append(" order by e.captured_at desc, e.id desc limit ?");
        params.add(safeLimit);

        return jdbcTemplate.query(sql.toString(), ps -> {
            int index = 1;
            for (Object param : params) {
                if (param instanceof SqlArrayParameter array) ps.setArray(index++, ps.getConnection().createArrayOf(array.typeName(), array.values()));
                else ps.setObject(index++, param);
            }
        }, this::mapEventView);
    }

    private List<StoredEvent> storedEvents(UUID clientEventId) {
        return jdbcTemplate.query("select id, school_id from student_access_event where client_event_id = ?",
                (rs, rowNum) -> new StoredEvent(rs.getLong("id"), rs.getLong("school_id")), clientEventId);
    }

    private EventView eventView(long id, boolean duplicate) {
        return jdbcTemplate.queryForObject(
                "select e.id, e.client_event_id, e.event_type, e.captured_at, e.received_at, e.captured_offline, e.source_type, "
                        + "st.id student_id, st.registration, st.name student_name, c.id class_id, c.name class_name, s.id school_id, s.name school_name, "
                        + "exists(select 1 from student_access_notification n where n.event_id = e.id) notification_delivered "
                        + "from student_access_event e join student st on st.id = e.student_id left join school_class c on c.id = e.class_id join school_unit s on s.id = e.school_id where e.id = ?",
                (rs, rowNum) -> mapEventView(rs, rowNum, duplicate), id);
    }

    private EventView mapEventView(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return mapEventView(rs, rowNum, false);
    }

    private EventView mapEventView(java.sql.ResultSet rs, int rowNum, boolean duplicate) throws java.sql.SQLException {
        long classId = rs.getLong("class_id");
        Long nullableClassId = rs.wasNull() ? null : classId;
        return new EventView(rs.getObject("client_event_id", UUID.class), rs.getLong("student_id"), rs.getString("registration"), rs.getString("student_name"),
                nullableClassId, rs.getString("class_name"), rs.getLong("school_id"), rs.getString("school_name"), rs.getString("event_type"),
                rs.getObject("captured_at", OffsetDateTime.class), rs.getObject("received_at", OffsetDateTime.class), rs.getBoolean("captured_offline"),
                rs.getString("source_type"), true, duplicate, rs.getBoolean("notification_delivered"));
    }

    private void validateCapturedAt(OffsetDateTime capturedAt) {
        if (capturedAt == null) throw new IllegalArgumentException("Informe a data e hora de captura do evento.");
        if (capturedAt.isAfter(OffsetDateTime.now().plusMinutes(5))) throw new IllegalArgumentException("A data e hora de captura não pode estar no futuro.");
    }

    private String normalizeEventType(String eventType) {
        String value = eventType == null ? "" : eventType.trim().toUpperCase();
        if (!(value.equals("ENTRY") || value.equals("EXIT"))) throw new IllegalArgumentException("Selecione Entrada ou Saída.");
        return value;
    }

    private String normalizeSourceType(String sourceType) {
        String value = sourceType == null ? "" : sourceType.trim().toUpperCase();
        if (!(value.equals("QR") || value.equals("MANUAL"))) throw new IllegalArgumentException("Origem de leitura inválida.");
        return value;
    }

    private String normalizeDeviceId(String deviceId) {
        return deviceId == null || deviceId.isBlank() ? null : deviceId.trim();
    }

    private record StoredEvent(Long id, Long schoolId) {}
    private record SqlArrayParameter(String typeName, Object[] values) {}

    public record EventRequest(@NotNull(message = "Informe o identificador único do evento.") UUID clientEventId,
                               @NotBlank(message = "Leia o QR Code ou informe a matrícula.") String code,
                               @NotBlank(message = "Selecione Entrada ou Saída.") String eventType,
                               @NotNull(message = "Informe a data e hora da captura.") OffsetDateTime capturedAt,
                               boolean capturedOffline,
                               @NotBlank(message = "Informe a origem da identificação.") String sourceType,
                               @Size(max = 160, message = "Identificador do dispositivo muito longo.") String deviceId) {}

    public record EventView(UUID clientEventId, Long studentId, String registration, String studentName, Long classId, String className,
                            Long schoolId, String schoolName, String eventType, OffsetDateTime capturedAt, OffsetDateTime receivedAt,
                            boolean capturedOffline, String sourceType, boolean synchronizedEvent, boolean duplicate, boolean notificationDelivered) {}
}
