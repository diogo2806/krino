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

    public AccessContextController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public AccessContext get(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) {
            throw new IllegalArgumentException("Não foi possível identificar o usuário autenticado.");
        }

        List<String> networkPermissions = jdbcTemplate.query(
                "select distinct p.code "
                        + "from user_role_assignment ura "
                        + "join access_role_permission rp on rp.role_id = ura.role_id "
                        + "join access_permission p on p.id = rp.permission_id "
                        + "where ura.user_id = ? and ura.scope_type = 'NETWORK' "
                        + "order by p.code",
                (rs, rowNum) -> rs.getString("code"),
                principal.id());

        return new AccessContext(principal.id(), principal.getUsername(), principal.displayName(), networkPermissions);
    }

    public record AccessContext(Long userId, String username, String displayName, List<String> networkPermissions) {}
}
