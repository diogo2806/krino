package br.com.krino.accesscontrol;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class AccessControlAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public AccessControlAccessService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    public void requireRead(long schoolId, Authentication authentication) {
        requireAny(schoolId, authentication, "ACCESS_CONTROL_READ", "ACCESS_CONTROL_WRITE", "ACCESS_CARD_MANAGE");
    }

    public void requireWrite(long schoolId, Authentication authentication) {
        requireAny(schoolId, authentication, "ACCESS_CONTROL_WRITE");
    }

    public void requireCardManage(long schoolId, Authentication authentication) {
        requireAny(schoolId, authentication, "ACCESS_CARD_MANAGE");
    }

    public List<Long> accessibleSchoolIds(Authentication authentication) {
        if (hasAnyNetwork(authentication, "ACCESS_CONTROL_READ", "ACCESS_CONTROL_WRITE", "ACCESS_CARD_MANAGE")) {
            return jdbcTemplate.query("select id from school_unit where active = true order by name", (rs, rowNum) -> rs.getLong("id"));
        }
        Long userId = principalId(authentication);
        return jdbcTemplate.query(
                "select distinct s.id from school_unit s join user_role_assignment ura on ura.scope_type = 'SCHOOL' and ura.scope_reference = s.code "
                        + "join access_role_permission rp on rp.role_id = ura.role_id join access_permission p on p.id = rp.permission_id "
                        + "where ura.user_id = ? and p.code in ('ACCESS_CONTROL_READ', 'ACCESS_CONTROL_WRITE', 'ACCESS_CARD_MANAGE') and s.active = true order by s.id",
                (rs, rowNum) -> rs.getLong("id"), userId);
    }

    private void requireAny(long schoolId, Authentication authentication, String... permissions) {
        String code = schoolCode(schoolId);
        for (String permission : permissions) {
            if (authorizationService.hasSchoolPermission(authentication, permission, code)) return;
        }
        throw new AccessDeniedException("Sua conta não possui permissão para esta operação de entrada e saída nesta unidade escolar.");
    }

    private boolean hasAnyNetwork(Authentication authentication, String... permissions) {
        for (String permission : permissions) {
            if (authorizationService.hasNetworkPermission(authentication, permission)) return true;
        }
        return false;
    }

    private Long principalId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) throw new AccessDeniedException("Usuário autenticado não identificado.");
        return principal.id();
    }

    private String schoolCode(long schoolId) {
        List<String> rows = jdbcTemplate.query("select code from school_unit where id = ? and active = true", (rs, rowNum) -> rs.getString("code"), schoolId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unidade escolar não encontrada ou inativa.");
        return rows.getFirst();
    }
}
