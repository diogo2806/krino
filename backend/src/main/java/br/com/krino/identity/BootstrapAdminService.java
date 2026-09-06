package br.com.krino.identity;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;

@Component
public class BootstrapAdminService implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminService.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final String username;
    private final String password;

    public BootstrapAdminService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            SecurityAuditService auditService,
            @Value("${krino.security.bootstrap-admin-username:}") String username,
            @Value("${krino.security.bootstrap-admin-password:}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer users = jdbcTemplate.queryForObject("select count(*) from app_user", Integer.class);
        if (users != null && users > 0) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            LOGGER.warn("Nenhum usuário existe. Defina BOOTSTRAP_ADMIN_USERNAME e BOOTSTRAP_ADMIN_PASSWORD para criar o primeiro administrador.");
            return;
        }
        if (password.length() < 12) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD deve possuir pelo menos 12 caracteres.");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into app_user (username, display_name, password_hash, active) values (?, ?, ?, true)",
                    new String[] { "id" });
            statement.setString(1, username.trim());
            statement.setString(2, "Administrador do sistema");
            statement.setString(3, passwordEncoder.encode(password));
            return statement;
        }, keyHolder);
        long userId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        Long roleId = jdbcTemplate.queryForObject("select id from access_role where name = 'Administrador do sistema'", Long.class);
        jdbcTemplate.update("insert into user_role_assignment (user_id, role_id, scope_type, scope_reference) values (?, ?, 'NETWORK', null)", userId, roleId);
        auditService.record("SYSTEM", "BOOTSTRAP_ADMIN_CREATED", "USER", Long.toString(userId), "Primeiro administrador criado por configuração externa.");
        LOGGER.info("Primeiro administrador do KRINO criado com sucesso.");
    }
}
