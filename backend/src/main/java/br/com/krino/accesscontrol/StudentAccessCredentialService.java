package br.com.krino.accesscontrol;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import br.com.krino.audit.SecurityAuditService;

@Service
public class StudentAccessCredentialService {

    private static final String QR_PREFIX = "KRINO-STUDENT:";

    private final JdbcTemplate jdbcTemplate;
    private final AccessControlAccessService accessService;
    private final SecurityAuditService auditService;

    public StudentAccessCredentialService(JdbcTemplate jdbcTemplate, AccessControlAccessService accessService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    public StudentIdentity identify(String code, Authentication authentication) {
        StudentIdentity identity = resolve(code);
        accessService.requireRead(identity.schoolId(), authentication);
        return identity;
    }

    public StudentIdentity resolve(String code) {
        String normalized = normalize(code);
        String token = normalized.startsWith(QR_PREFIX) ? normalized.substring(QR_PREFIX.length()) : normalized;
        List<StudentIdentity> rows = jdbcTemplate.query(
                "select st.id student_id, st.registration, st.name student_name, c.id class_id, c.name class_name, c.school_id, s.name school_name "
                        + "from student st join student_enrollment e on e.student_id = st.id and e.status = 'ACTIVE' "
                        + "join school_class c on c.id = e.class_id join school_unit s on s.id = c.school_id "
                        + "left join student_access_credential cred on cred.student_id = st.id and cred.active = true "
                        + "where st.status = 'ACTIVE' and (cred.credential_token = ? or lower(st.registration) = lower(?)) "
                        + "order by e.academic_year desc, e.enrollment_date desc limit 1",
                (rs, rowNum) -> new StudentIdentity(rs.getLong("student_id"), rs.getString("registration"), rs.getString("student_name"),
                        rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("school_id"), rs.getString("school_name")),
                token, normalized);
        if (rows.isEmpty()) throw new IllegalArgumentException("Não foi possível identificar o estudante. Tente novamente ou use a matrícula no campo de código manual.");
        return rows.getFirst();
    }

    @Transactional
    public CardView issueCard(long studentId, Authentication authentication) {
        StudentIdentity identity = currentIdentity(studentId);
        accessService.requireCardManage(identity.schoolId(), authentication);
        List<String> tokens = jdbcTemplate.query(
                "select credential_token from student_access_credential where student_id = ? and active = true",
                (rs, rowNum) -> rs.getString("credential_token"), studentId);
        String token;
        if (tokens.isEmpty()) {
            token = UUID.randomUUID().toString();
            jdbcTemplate.update("insert into student_access_credential (student_id, credential_token, active) values (?, ?, true) "
                    + "on conflict (student_id) do update set credential_token = excluded.credential_token, active = true, updated_at = current_timestamp",
                    studentId, token);
            auditService.record(authentication.getName(), "STUDENT_ACCESS_CARD_ISSUED", "STUDENT", Long.toString(studentId), "Credencial QR emitida para controle de entrada e saída.");
        } else {
            token = tokens.getFirst();
        }
        String payload = QR_PREFIX + token;
        return new CardView(identity.studentId(), identity.registration(), identity.studentName(), identity.className(), identity.schoolName(), payload, qrSvg(payload));
    }

    private StudentIdentity currentIdentity(long studentId) {
        List<StudentIdentity> rows = jdbcTemplate.query(
                "select st.id student_id, st.registration, st.name student_name, c.id class_id, c.name class_name, c.school_id, s.name school_name "
                        + "from student st join student_enrollment e on e.student_id = st.id and e.status = 'ACTIVE' "
                        + "join school_class c on c.id = e.class_id join school_unit s on s.id = c.school_id "
                        + "where st.id = ? and st.status = 'ACTIVE' order by e.academic_year desc, e.enrollment_date desc limit 1",
                (rs, rowNum) -> new StudentIdentity(rs.getLong("student_id"), rs.getString("registration"), rs.getString("student_name"),
                        rs.getLong("class_id"), rs.getString("class_name"), rs.getLong("school_id"), rs.getString("school_name")), studentId);
        if (rows.isEmpty()) throw new IllegalArgumentException("O estudante precisa possuir matrícula ativa para emitir a carteirinha.");
        return rows.getFirst();
    }

    private String qrSvg(String payload) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 1, 1, hints);
            StringBuilder svg = new StringBuilder();
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(matrix.getWidth()).append(' ').append(matrix.getHeight()).append("\" shape-rendering=\"crispEdges\">");
            svg.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>");
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) svg.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"1\" height=\"1\" fill=\"black\"/>");
                }
            }
            return svg.append("</svg>").toString();
        } catch (WriterException exception) {
            throw new IllegalStateException("Não foi possível gerar o QR Code da carteirinha.", exception);
        }
    }

    private String normalize(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Leia o QR Code ou informe a matrícula do estudante.");
        return code.trim();
    }

    public record StudentIdentity(Long studentId, String registration, String studentName, Long classId, String className, Long schoolId, String schoolName) {}
    public record CardView(Long studentId, String registration, String studentName, String className, String schoolName, String qrPayload, String qrSvg) {}
}
