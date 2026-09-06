package br.com.krino.family;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class FamilyPortalAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public FamilyPortalAccessService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    public long userId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) {
            throw new AccessDeniedException("Usuário autenticado não identificado.");
        }
        return principal.id();
    }

    public void requirePortalPermission(Authentication authentication) {
        if (!authorizationService.hasPermission(authentication, "STUDENT_LINKED_READ")) {
            throw new AccessDeniedException("Sua conta não possui permissão para acessar o Portal do Responsável.");
        }
    }

    public void requireLinkedStudent(long studentId, Authentication authentication) {
        if (!authorizationService.canResponsibleAccessStudent(authentication, Long.toString(studentId))) {
            throw new AccessDeniedException("Sua conta não possui vínculo autorizado com este estudante.");
        }
    }

    public void requireSchoolRead(long schoolId, Authentication authentication) {
        String code = schoolCode(schoolId);
        if (!(authorizationService.hasSchoolPermission(authentication, "FAMILY_COMMUNICATION_READ", code)
                || authorizationService.hasSchoolPermission(authentication, "FAMILY_COMMUNICATION_WRITE", code))) {
            throw new AccessDeniedException("Sua conta não possui permissão para consultar a comunicação desta unidade escolar.");
        }
    }

    public void requireSchoolWrite(long schoolId, Authentication authentication) {
        String code = schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "FAMILY_COMMUNICATION_WRITE", code)) {
            throw new AccessDeniedException("Sua conta não possui permissão para responder mensagens ou publicar comunicados nesta unidade escolar.");
        }
    }

    public List<Long> accessibleSchools(Authentication authentication) {
        if (authorizationService.hasNetworkPermission(authentication, "FAMILY_COMMUNICATION_READ")
                || authorizationService.hasNetworkPermission(authentication, "FAMILY_COMMUNICATION_WRITE")) {
            return jdbcTemplate.query("select id from school_unit where active = true order by name", (rs, rowNum) -> rs.getLong("id"));
        }
        long userId = userId(authentication);
        return jdbcTemplate.query(
                "select distinct s.id from school_unit s "
                        + "join user_role_assignment ura on ura.scope_type = 'SCHOOL' and ura.scope_reference = s.code "
                        + "join access_role_permission rp on rp.role_id = ura.role_id "
                        + "join access_permission p on p.id = rp.permission_id "
                        + "where ura.user_id = ? and p.code in ('FAMILY_COMMUNICATION_READ', 'FAMILY_COMMUNICATION_WRITE') and s.active = true order by s.id",
                (rs, rowNum) -> rs.getLong("id"), userId);
    }

    private String schoolCode(long schoolId) {
        List<String> rows = jdbcTemplate.query("select code from school_unit where id = ? and active = true",
                (rs, rowNum) -> rs.getString("code"), schoolId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unidade escolar não encontrada ou inativa.");
        return rows.getFirst();
    }
}
