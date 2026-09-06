package br.com.krino.accesscontrol;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StudentAccessNotificationService {

    private final JdbcTemplate jdbcTemplate;

    public StudentAccessNotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void issue(long eventId, long studentId, String eventType, OffsetDateTime capturedAt) {
        String studentName = jdbcTemplate.queryForObject("select name from student where id = ?", String.class, studentId);
        String guardianName = jdbcTemplate.queryForObject("select guardian_name from student where id = ?", String.class, studentId);
        String action = eventType.equals("ENTRY") ? "Entrada" : "Saída";
        String message = action + " registrada para " + studentName + " em " + capturedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + ".";
        jdbcTemplate.update(
                "insert into student_access_notification (event_id, student_id, guardian_name, channel, message, delivered_at) values (?, ?, ?, 'IN_APP', ?, current_timestamp) on conflict (event_id) do nothing",
                eventId, studentId, guardianName, message);
    }
}
