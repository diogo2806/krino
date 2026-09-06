package br.com.krino.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("authorizationService")
public class AuthorizationService {

    private final JdbcTemplate jdbcTemplate;

    public AuthorizationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasPermission(Authentication authentication, String permissionCode) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(permissionCode));
    }

    public boolean hasNetworkPermission(Authentication authentication, String permissionCode) {
        Long userId = userId(authentication);
        if (userId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_role_assignment ura join access_role_permission rp on rp.role_id = ura.role_id join access_permission p on p.id = rp.permission_id where ura.user_id = ? and ura.scope_type = 'NETWORK' and p.code = ?",
                Integer.class,
                userId,
                permissionCode);
        return count != null && count > 0;
    }

    public boolean hasSchoolPermission(Authentication authentication, String permissionCode, String schoolReference) {
        Long userId = userId(authentication);
        if (userId == null || schoolReference == null || schoolReference.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_role_assignment ura join access_role_permission rp on rp.role_id = ura.role_id join access_permission p on p.id = rp.permission_id where ura.user_id = ? and p.code = ? and (ura.scope_type = 'NETWORK' or (ura.scope_type = 'SCHOOL' and ura.scope_reference = ?))",
                Integer.class,
                userId,
                permissionCode,
                schoolReference);
        return count != null && count > 0;
    }

    public boolean canResponsibleAccessStudent(Authentication authentication, String studentReference) {
        return hasLinkedResource(authentication, "STUDENT", studentReference, "READ")
                && hasPermission(authentication, "STUDENT_LINKED_READ");
    }

    public boolean canProfessorEditDiary(Authentication authentication, String diaryReference) {
        return hasLinkedResource(authentication, "DIARY", diaryReference, "EDIT")
                && hasPermission(authentication, "DIARY_ASSIGNED_EDIT");
    }

    @Transactional
    public void linkResource(long userId, String resourceType, String resourceReference, String accessLevel) {
        if (!(resourceType.equals("STUDENT") || resourceType.equals("DIARY"))) {
            throw new IllegalArgumentException("Tipo de recurso de acesso inválido.");
        }
        if (!(accessLevel.equals("READ") || accessLevel.equals("EDIT"))) {
            throw new IllegalArgumentException("Nível de acesso inválido.");
        }
        jdbcTemplate.update(
                "insert into linked_resource_access (user_id, resource_type, resource_reference, access_level) values (?, ?, ?, ?) on conflict (user_id, resource_type, resource_reference) do update set access_level = excluded.access_level",
                userId, resourceType, resourceReference, accessLevel);
    }

    @Transactional
    public void unlinkResource(long userId, String resourceType, String resourceReference) {
        jdbcTemplate.update("delete from linked_resource_access where user_id = ? and resource_type = ? and resource_reference = ?", userId, resourceType, resourceReference);
    }

    private boolean hasLinkedResource(Authentication authentication, String type, String reference, String requiredLevel) {
        Long userId = userId(authentication);
        if (userId == null || reference == null || reference.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from linked_resource_access where user_id = ? and resource_type = ? and resource_reference = ? and (access_level = ? or access_level = 'EDIT')",
                Integer.class,
                userId,
                type,
                reference,
                requiredLevel);
        return count != null && count > 0;
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) {
            return null;
        }
        return principal.id();
    }
}
