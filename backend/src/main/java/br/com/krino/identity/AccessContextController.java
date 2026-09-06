package br.com.krino.identity;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.krino.security.KrinoUserPrincipal;

@RestController
@RequestMapping("/api/auth/access-context")
public class AccessContextController {

    private final JdbcTemplate jdbcTemplate;

    public AccessContextController(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping
    public AccessContext get(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) throw new IllegalArgumentException("Não foi possível identificar o usuário autenticado.");

        List<String> allPermissions = principal.getAuthorities().stream().map(authority -> authority.getAuthority()).distinct().sorted().toList();
        List<String> networkPermissions = jdbcTemplate.query(
                "select distinct p.code from user_role_assignment ura join access_role_permission rp on rp.role_id = ura.role_id join access_permission p on p.id = rp.permission_id where ura.user_id = ? and ura.scope_type = 'NETWORK' order by p.code",
                (rs, rowNum) -> rs.getString("code"), principal.id());
        List<String> schoolScopes = jdbcTemplate.query(
                "select distinct ura.scope_reference from user_role_assignment ura where ura.user_id = ? and ura.scope_type = 'SCHOOL' and ura.scope_reference is not null order by ura.scope_reference",
                (rs, rowNum) -> rs.getString("scope_reference"), principal.id());

        return new AccessContext(principal.id(), principal.getUsername(), principal.displayName(), allPermissions, networkPermissions, schoolScopes);
    }

    public record AccessContext(Long userId, String username, String displayName, List<String> permissions, List<String> networkPermissions, List<String> schoolScopes) {}
}
