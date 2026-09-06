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
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final IdentityService identityService;

    public UserAdminController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'USER_READ')")
    public List<IdentityService.UserView> list() {
        return identityService.listUsers();
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'USER_WRITE')")
    public IdentityService.UserView create(@Valid @RequestBody IdentityService.CreateUserRequest request, Authentication authentication) {
        return identityService.createUser(request, authentication.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'USER_WRITE')")
    public IdentityService.UserView update(@PathVariable long id, @Valid @RequestBody IdentityService.UpdateUserRequest request, Authentication authentication) {
        return identityService.updateUser(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'USER_WRITE')")
    public void deactivate(@PathVariable long id, Authentication authentication) {
        identityService.deactivateUser(id, authentication.getName());
    }

    @PostMapping("/{id}/password")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'USER_WRITE')")
    public void resetPassword(@PathVariable long id, @Valid @RequestBody IdentityService.ResetPasswordRequest request, Authentication authentication) {
        identityService.resetPassword(id, request, authentication.getName());
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'SCOPE_ASSIGN')")
    public IdentityService.UserView assignRole(@PathVariable long id, @Valid @RequestBody IdentityService.RoleAssignmentRequest request, Authentication authentication) {
        return identityService.assignRole(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}/roles/{assignmentId}")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'SCOPE_ASSIGN')")
    public IdentityService.UserView removeRole(@PathVariable long id, @PathVariable long assignmentId, Authentication authentication) {
        return identityService.removeRoleAssignment(id, assignmentId, authentication.getName());
    }
}
