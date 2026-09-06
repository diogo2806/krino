package br.com.krino.security;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var users = jdbcTemplate.query(
                "select id, username, display_name, password_hash, active from app_user where lower(username) = lower(?)",
                (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("password_hash"), rs.getBoolean("active")),
                username);

        if (users.isEmpty()) {
            throw new UsernameNotFoundException("Usuário não encontrado.");
        }

        var user = users.getFirst();
        List<SimpleGrantedAuthority> authorities = jdbcTemplate.query(
                "select distinct p.code from user_role_assignment ura join access_role_permission rp on rp.role_id = ura.role_id join access_permission p on p.id = rp.permission_id where ura.user_id = ? order by p.code",
                (rs, rowNum) -> new SimpleGrantedAuthority(rs.getString("code")),
                user.id());

        return new KrinoUserPrincipal(user.id(), user.username(), user.passwordHash(), user.displayName(), user.active(), authorities);
    }

    private record UserRow(Long id, String username, String displayName, String passwordHash, boolean active) {}
}
