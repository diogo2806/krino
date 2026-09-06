package br.com.krino.monitoring;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class MonitoringAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public MonitoringAccessService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    public boolean canNetworkRead(Authentication authentication) {
        return authorizationService.hasNetworkPermission(authentication, "MONITORING_READ")
                || authorizationService.hasNetworkPermission(authentication, "MONITORING_MANAGE");
    }

    public boolean canNetworkManage(Authentication authentication) {
        return authorizationService.hasNetworkPermission(authentication, "MONITORING_MANAGE");
    }

    public void requireNetworkRead(Authentication authentication) {
        if (!canNetworkRead(authentication)) throw new AccessDeniedException("Sua conta não possui permissão para consultar o monitoramento de toda a Rede.");
    }

    public void requireRead(long schoolId, Authentication authentication) {
        String code = schoolCode(schoolId);
        if (!(authorizationService.hasSchoolPermission(authentication, "MONITORING_READ", code)
                || authorizationService.hasSchoolPermission(authentication, "MONITORING_MANAGE", code))) {
            throw new AccessDeniedException("Sua conta não possui permissão para consultar o monitoramento desta unidade escolar.");
        }
    }

    public void requireManage(Long schoolId, Authentication authentication) {
        if (schoolId == null) {
            if (!canNetworkManage(authentication)) throw new AccessDeniedException("Sua conta não possui permissão para registrar cenários no escopo da Rede.");
            return;
        }
        String code = schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "MONITORING_MANAGE", code)) {
            throw new AccessDeniedException("Sua conta não possui permissão para registrar cenários nesta unidade escolar.");
        }
    }

    public List<Long> accessibleSchoolIds(Authentication authentication) {
        if (canNetworkRead(authentication)) {
            return jdbcTemplate.query("select id from school_unit where active = true order by name", (rs, rowNum) -> rs.getLong("id"));
        }
        Long userId = principalId(authentication);
        return jdbcTemplate.query(
                "select distinct s.id from school_unit s join user_role_assignment ura on ura.scope_type = 'SCHOOL' and ura.scope_reference = s.code "
                        + "join access_role_permission rp on rp.role_id = ura.role_id join access_permission p on p.id = rp.permission_id "
                        + "where ura.user_id = ? and p.code in ('MONITORING_READ', 'MONITORING_MANAGE') and s.active = true order by s.id",
                (rs, rowNum) -> rs.getLong("id"), userId);
    }

    private Long principalId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) throw new AccessDeniedException("Usuário autenticado não identificado.");
        return principal.id();
    }

    private String schoolCode(long schoolId) {
        List<String> rows = jdbcTemplate.query("select code from school_unit where id = ?", (rs, rowNum) -> rs.getString("code"), schoolId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unidade escolar não encontrada.");
        return rows.getFirst();
    }
}
