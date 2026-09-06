package br.com.krino.identity;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class RoleAdminController {

    private final IdentityService identityService;

    public RoleAdminController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/roles")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'ROLE_READ')")
    public List<IdentityService.RoleView> roles() {
        return identityService.listRoles();
    }

    @PostMapping("/roles")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'ROLE_WRITE')")
    public IdentityService.RoleView createRole(@Valid @RequestBody IdentityService.CreateRoleRequest request, Authentication authentication) {
        return identityService.createRole(request, authentication.getName());
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'ROLE_WRITE')")
    public IdentityService.RoleView updateRole(@PathVariable long id, @Valid @RequestBody IdentityService.CreateRoleRequest request, Authentication authentication) {
        return identityService.updateRole(id, request, authentication.getName());
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'ROLE_WRITE')")
    public void deleteRole(@PathVariable long id, Authentication authentication) {
        identityService.deleteRole(id, authentication.getName());
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'ROLE_WRITE')")
    public IdentityService.RoleView permissions(@PathVariable long id, @RequestBody IdentityService.SetRolePermissionsRequest request, Authentication authentication) {
        return identityService.setRolePermissions(id, request, authentication.getName());
    }

    @GetMapping("/permissions")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'ROLE_READ')")
    public List<IdentityService.PermissionView> permissions() {
        return identityService.listPermissions();
    }
}
