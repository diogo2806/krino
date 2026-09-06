package br.com.krino.diary;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class DiaryAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public DiaryAccessService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    public DiaryContext requireRead(long diaryId, Authentication authentication) {
        DiaryContext context = context(diaryId);
        requireSchoolRead(context.schoolId(), authentication);
        return context;
    }

    public DiaryContext requireAdmin(long diaryId, Authentication authentication) {
        DiaryContext context = context(diaryId);
        requireSchoolAdmin(context.schoolId(), authentication);
        return context;
    }

    public DiaryContext requireEdit(long diaryId, Authentication authentication) {
        DiaryContext context = context(diaryId);
        if (authorizationService.hasSchoolPermission(authentication, "DIARY_ADMIN", context.schoolCode())) return context;
        if (!authorizationService.hasSchoolPermission(authentication, "DIARY_EDIT", context.schoolCode())) {
            throw new AccessDeniedException("Sua conta não possui permissão para editar Diário de Classe.");
        }
        Long professionalId = linkedProfessionalId(authentication);
        if (professionalId == null || !professionalId.equals(context.responsibleProfessionalId())) {
            throw new AccessDeniedException("Somente o professor responsável por este diário pode editar os lançamentos.");
        }
        return context;
    }

    public boolean canEdit(long diaryId, Authentication authentication) {
        try {
            requireEdit(diaryId, authentication);
            return true;
        } catch (AccessDeniedException exception) {
            return false;
        }
    }

    public void requireSchoolRead(long schoolId, Authentication authentication) {
        String schoolCode = schoolCode(schoolId);
        if (!(authorizationService.hasSchoolPermission(authentication, "DIARY_READ", schoolCode)
                || authorizationService.hasSchoolPermission(authentication, "DIARY_ADMIN", schoolCode))) {
            throw new AccessDeniedException("Sua conta não possui permissão para consultar os diários desta unidade escolar.");
        }
    }

    public void requireSchoolAdmin(long schoolId, Authentication authentication) {
        String schoolCode = schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "DIARY_ADMIN", schoolCode)) {
            throw new AccessDeniedException("Sua conta não possui permissão para administrar diários nesta unidade escolar.");
        }
    }

    public void requireCurriculumManage(long schoolId, Authentication authentication) {
        String schoolCode = schoolCode(schoolId);
        if (!(authorizationService.hasSchoolPermission(authentication, "CURRICULUM_MANAGE", schoolCode)
                || authorizationService.hasSchoolPermission(authentication, "DIARY_ADMIN", schoolCode))) {
            throw new AccessDeniedException("Sua conta não possui permissão para cadastrar referências curriculares.");
        }
    }

    public DiaryContext context(long diaryId) {
        List<DiaryContext> rows = jdbcTemplate.query(
                "select d.id, d.class_id, c.school_id, s.code school_code, c.stage, d.component_id, d.responsible_professional_id, d.mode, d.valid_from, d.valid_until "
                        + "from class_diary d join school_class c on c.id = d.class_id join school_unit s on s.id = c.school_id where d.id = ? and d.active = true",
                (rs, rowNum) -> {
                    java.sql.Date until = rs.getDate("valid_until");
                    return new DiaryContext(rs.getLong("id"), rs.getLong("class_id"), rs.getLong("school_id"), rs.getString("school_code"), rs.getString("stage"), nullableLong(rs, "component_id"), rs.getLong("responsible_professional_id"), rs.getString("mode"), rs.getDate("valid_from").toLocalDate(), until == null ? null : until.toLocalDate());
                }, diaryId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Diário de Classe não encontrado.");
        return rows.getFirst();
    }

    private Long linkedProfessionalId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) return null;
        List<Long> ids = jdbcTemplate.query("select professional_id from professional_user_account where user_id = ?", (rs, rowNum) -> rs.getLong(1), principal.id());
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private String schoolCode(long schoolId) {
        List<String> codes = jdbcTemplate.query("select code from school_unit where id = ?", (rs, rowNum) -> rs.getString(1), schoolId);
        if (codes.isEmpty()) throw new IllegalArgumentException("Unidade escolar não encontrada.");
        return codes.getFirst();
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record DiaryContext(Long diaryId, Long classId, Long schoolId, String schoolCode, String stage, Long componentId,
                               Long responsibleProfessionalId, String mode, java.time.LocalDate validFrom, java.time.LocalDate validUntil) {}
}
