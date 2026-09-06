package br.com.krino.identity;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;

@Service
public class IdentityService {

    private static final String ADMIN_ROLE = "Administrador do sistema";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;

    public IdentityService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<UserView> listUsers() {
        return jdbcTemplate.query("select id, username, display_name, active from app_user order by display_name, username",
                (rs, rowNum) -> new UserCore(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getBoolean("active")))
                .stream().map(this::toUserView).toList();
    }

    public UserView findByUsername(String username) {
        var users = jdbcTemplate.query("select id, username, display_name, active from app_user where lower(username) = lower(?)",
                (rs, rowNum) -> new UserCore(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getBoolean("active")), username);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        return toUserView(users.getFirst());
    }

    @Transactional
    public UserView createUser(CreateUserRequest request, String actor) {
        validatePassword(request.password());
        String username = request.username().trim();
        String displayName = request.displayName().trim();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into app_user (username, display_name, password_hash, active) values (?, ?, ?, true)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, displayName);
            statement.setString(3, passwordEncoder.encode(request.password()));
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        auditService.record(actor, "USER_CREATED", "USER", Long.toString(id), "Usuário criado: " + username);
        return getUser(id);
    }

    @Transactional
    public UserView updateUser(long id, UpdateUserRequest request, String actor) {
        getUser(id);
        int updated = jdbcTemplate.update(
                "update app_user set username = ?, display_name = ?, active = ?, updated_at = current_timestamp where id = ?",
                request.username().trim(), request.displayName().trim(), request.active(), id);
        if (updated == 0) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        auditService.record(actor, "USER_UPDATED", "USER", Long.toString(id), "Dados cadastrais e estado atualizados.");
        return getUser(id);
    }

    @Transactional
    public void deactivateUser(long id, String actor) {
        UserView user = getUser(id);
        if (user.username().equalsIgnoreCase(actor)) {
            throw new IllegalArgumentException("Não é possível desativar o próprio usuário.");
        }
        jdbcTemplate.update("update app_user set active = false, updated_at = current_timestamp where id = ?", id);
        auditService.record(actor, "USER_DEACTIVATED", "USER", Long.toString(id), "Usuário desativado: " + user.username());
    }

    @Transactional
    public void resetPassword(long id, ResetPasswordRequest request, String actor) {
        validatePassword(request.newPassword());
        getUser(id);
        jdbcTemplate.update("update app_user set password_hash = ?, updated_at = current_timestamp where id = ?", passwordEncoder.encode(request.newPassword()), id);
        auditService.record(actor, "USER_PASSWORD_RESET", "USER", Long.toString(id), "Senha redefinida por usuário autorizado.");
    }

    @Transactional
    public UserView assignRole(long userId, RoleAssignmentRequest request, String actor) {
        getUser(userId);
        getRole(request.roleId());
        ScopeType scopeType;
        try {
            scopeType = ScopeType.valueOf(request.scopeType().trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Escopo de acesso inválido.");
        }
        String scopeReference = switch (scopeType) {
            case NETWORK -> null;
            case SCHOOL -> requireReference(request.scopeReference(), "Informe a unidade escolar do escopo.");
            case USER -> request.scopeReference() == null || request.scopeReference().isBlank() ? Long.toString(userId) : request.scopeReference().trim();
        };
        jdbcTemplate.update("insert into user_role_assignment (user_id, role_id, scope_type, scope_reference) values (?, ?, ?, ?)",
                userId, request.roleId(), scopeType.name(), scopeReference);
        auditService.record(actor, "USER_ROLE_ASSIGNED", "USER", Long.toString(userId), "Perfil " + request.roleId() + " atribuído no escopo " + scopeType.name());
        return getUser(userId);
    }

    @Transactional
    public UserView removeRoleAssignment(long userId, long assignmentId, String actor) {
        int deleted = jdbcTemplate.update("delete from user_role_assignment where id = ? and user_id = ?", assignmentId, userId);
        if (deleted == 0) {
            throw new IllegalArgumentException("Vínculo de perfil não encontrado para este usuário.");
        }
        auditService.record(actor, "USER_ROLE_REMOVED", "USER", Long.toString(userId), "Vínculo de perfil removido: " + assignmentId);
        return getUser(userId);
    }

    public List<RoleView> listRoles() {
        return jdbcTemplate.query("select id, name, description, system_role from access_role order by name",
                (rs, rowNum) -> new RoleCore(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getBoolean("system_role")))
                .stream().map(this::toRoleView).toList();
    }

    public List<PermissionView> listPermissions() {
        return jdbcTemplate.query("select id, code, name, description from access_permission order by name",
                (rs, rowNum) -> new PermissionView(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("description")));
    }

    @Transactional
    public RoleView createRole(CreateRoleRequest request, String actor) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into access_role (name, description, system_role) values (?, ?, false)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.name().trim());
            statement.setString(2, request.description());
            return statement;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        auditService.record(actor, "ROLE_CREATED", "ROLE", Long.toString(id), "Perfil criado: " + request.name().trim());
        return getRole(id);
    }

    @Transactional
    public RoleView updateRole(long id, CreateRoleRequest request, String actor) {
        getRole(id);
        jdbcTemplate.update("update access_role set name = ?, description = ? where id = ?", request.name().trim(), request.description(), id);
        auditService.record(actor, "ROLE_UPDATED", "ROLE", Long.toString(id), "Perfil atualizado.");
        return getRole(id);
    }

    @Transactional
    public void deleteRole(long id, String actor) {
        RoleView role = getRole(id);
        if (role.systemRole()) {
            throw new IllegalArgumentException("Perfis-base do sistema não podem ser excluídos; ajuste suas permissões quando necessário.");
        }
        Integer assignments = jdbcTemplate.queryForObject("select count(*) from user_role_assignment where role_id = ?", Integer.class, id);
        if (assignments != null && assignments > 0) {
            throw new IllegalArgumentException("Remova este perfil dos usuários antes de excluí-lo.");
        }
        jdbcTemplate.update("delete from access_role where id = ?", id);
        auditService.record(actor, "ROLE_DELETED", "ROLE", Long.toString(id), "Perfil excluído: " + role.name());
    }

    @Transactional
    public RoleView setRolePermissions(long roleId, SetRolePermissionsRequest request, String actor) {
        RoleView role = getRole(roleId);
        List<Long> ids = request.permissionIds() == null ? List.of() : request.permissionIds().stream().distinct().toList();
        if (role.name().equalsIgnoreCase(ADMIN_ROLE)) {
            ensurePermissionPresent(ids, "USER_WRITE");
            ensurePermissionPresent(ids, "ROLE_WRITE");
        }
        jdbcTemplate.update("delete from access_role_permission where role_id = ?", roleId);
        for (Long permissionId : ids) {
            Integer exists = jdbcTemplate.queryForObject("select count(*) from access_permission where id = ?", Integer.class, permissionId);
            if (exists == null || exists == 0) {
                throw new IllegalArgumentException("Permissão informada não existe: " + permissionId);
            }
            jdbcTemplate.update("insert into access_role_permission (role_id, permission_id) values (?, ?)", roleId, permissionId);
        }
        auditService.record(actor, "ROLE_PERMISSIONS_UPDATED", "ROLE", Long.toString(roleId), "Permissões do perfil atualizadas.");
        return getRole(roleId);
    }

    public UserView getUser(long id) {
        var users = jdbcTemplate.query("select id, username, display_name, active from app_user where id = ?",
                (rs, rowNum) -> new UserCore(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getBoolean("active")), id);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }
        return toUserView(users.getFirst());
    }

    public RoleView getRole(long id) {
        var roles = jdbcTemplate.query("select id, name, description, system_role from access_role where id = ?",
                (rs, rowNum) -> new RoleCore(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getBoolean("system_role")), id);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Perfil não encontrado.");
        }
        return toRoleView(roles.getFirst());
    }

    private UserView toUserView(UserCore user) {
        List<RoleAssignmentView> assignments = jdbcTemplate.query(
                "select ura.id, r.id role_id, r.name role_name, ura.scope_type, ura.scope_reference from user_role_assignment ura join access_role r on r.id = ura.role_id where ura.user_id = ? order by r.name, ura.scope_type",
                (rs, rowNum) -> new RoleAssignmentView(rs.getLong("id"), rs.getLong("role_id"), rs.getString("role_name"), rs.getString("scope_type"), rs.getString("scope_reference")), user.id());
        return new UserView(user.id(), user.username(), user.displayName(), user.active(), assignments);
    }

    private RoleView toRoleView(RoleCore role) {
        List<PermissionView> permissions = jdbcTemplate.query(
                "select p.id, p.code, p.name, p.description from access_role_permission rp join access_permission p on p.id = rp.permission_id where rp.role_id = ? order by p.name",
                (rs, rowNum) -> new PermissionView(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("description")), role.id());
        return new RoleView(role.id(), role.name(), role.description(), role.systemRole(), permissions);
    }

    private void ensurePermissionPresent(List<Long> ids, String code) {
        Long permissionId = jdbcTemplate.queryForObject("select id from access_permission where code = ?", Long.class, code);
        if (permissionId != null && !ids.contains(permissionId)) {
            throw new IllegalArgumentException("O perfil Administrador do sistema deve manter as permissões essenciais de administração.");
        }
    }

    private String requireReference(String reference, String message) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return reference.trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("A senha deve possuir pelo menos 12 caracteres.");
        }
    }

    private record UserCore(Long id, String username, String displayName, boolean active) {}
    private record RoleCore(Long id, String name, String description, boolean systemRole) {}
    private enum ScopeType { NETWORK, SCHOOL, USER }

    public record CreateUserRequest(
            @NotBlank(message = "Informe o usuário.") String username,
            @NotBlank(message = "Informe o nome do usuário.") String displayName,
            @NotBlank(message = "Informe a senha.") @Size(min = 12, message = "A senha deve possuir pelo menos 12 caracteres.") String password) {}

    public record UpdateUserRequest(
            @NotBlank(message = "Informe o usuário.") String username,
            @NotBlank(message = "Informe o nome do usuário.") String displayName,
            boolean active) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "Informe a nova senha.") @Size(min = 12, message = "A senha deve possuir pelo menos 12 caracteres.") String newPassword) {}

    public record RoleAssignmentRequest(
            @NotNull(message = "Selecione o perfil.") Long roleId,
            @NotBlank(message = "Selecione o escopo de acesso.") String scopeType,
            String scopeReference) {}

    public record CreateRoleRequest(
            @NotBlank(message = "Informe o nome do perfil.") String name,
            String description) {}

    public record SetRolePermissionsRequest(List<Long> permissionIds) {}

    public record UserView(Long id, String username, String displayName, boolean active, List<RoleAssignmentView> assignments) {}
    public record RoleAssignmentView(Long id, Long roleId, String roleName, String scopeType, String scopeReference) {}
    public record RoleView(Long id, String name, String description, boolean systemRole, List<PermissionView> permissions) {}
    public record PermissionView(Long id, String code, String name, String description) {}
}
