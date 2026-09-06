package br.com.krino.secretaria;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class SchoolAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public SchoolAccessService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    public void requireRead(Authentication authentication, Long schoolId) {
        require(authentication, "SCHOOL_READ", schoolId);
    }

    public void requireWrite(Authentication authentication, Long schoolId) {
        require(authentication, "SCHOOL_WRITE", schoolId);
    }

    public void requireDocumentRead(Authentication authentication, Long schoolId) {
        require(authentication, "SCHOOL_DOCUMENT_READ", schoolId);
    }

    public void requireNetworkWrite(Authentication authentication) {
        if (!authorizationService.hasNetworkPermission(authentication, "SCHOOL_WRITE")) {
            throw new AccessDeniedException("A gestão da Rede exige permissão municipal.");
        }
    }

    public List<Long> accessibleSchoolIds(Authentication authentication, String permissionCode) {
        if (authorizationService.hasNetworkPermission(authentication, permissionCode)) {
            return jdbcTemplate.query("select id from school_unit where active = true order by name", (rs, rowNum) -> rs.getLong("id"));
        }
        Long userId = principalId(authentication);
        return jdbcTemplate.query(
                "select distinct s.id from school_unit s "
                        + "join user_role_assignment ura on ura.scope_type = 'SCHOOL' and ura.scope_reference = s.code "
                        + "join access_role_permission rp on rp.role_id = ura.role_id "
                        + "join access_permission p on p.id = rp.permission_id "
                        + "where ura.user_id = ? and p.code = ? and s.active = true order by s.id",
                (rs, rowNum) -> rs.getLong("id"),
                userId,
                permissionCode);
    }

    public String schoolCode(Long schoolId) {
        List<String> codes = jdbcTemplate.query("select code from school_unit where id = ?", (rs, rowNum) -> rs.getString("code"), schoolId);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("Unidade escolar não encontrada.");
        }
        return codes.getFirst();
    }

    private void require(Authentication authentication, String permissionCode, Long schoolId) {
        if (schoolId == null) {
            if (!authorizationService.hasNetworkPermission(authentication, permissionCode)) {
                throw new AccessDeniedException("Selecione uma unidade escolar autorizada para continuar.");
            }
            return;
        }
        String code = schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, permissionCode, code)) {
            throw new AccessDeniedException("Sua conta não possui acesso a esta unidade escolar.");
        }
    }

    private Long principalId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) {
            throw new AccessDeniedException("Usuário autenticado não identificado.");
        }
        return principal.id();
    }
}
