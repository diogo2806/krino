package br.com.krino.identity;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.krino.security.JwtService;
import br.com.krino.security.KrinoUserPrincipal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final IdentityService identityService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, IdentityService identityService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.identityService = identityService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            var principal = (KrinoUserPrincipal) authentication.getPrincipal();
            String token = jwtService.generateToken(principal);
            return ResponseEntity.ok(new LoginResponse(token, principal.id(), principal.getUsername(), principal.displayName(),
                    principal.getAuthorities().stream().map(authority -> authority.getAuthority()).sorted().toList()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Usuário ou senha inválidos."));
        }
    }

    @GetMapping("/me")
    public IdentityService.UserView me(org.springframework.security.core.Authentication authentication) {
        return identityService.findByUsername(authentication.getName());
    }

    public record LoginRequest(
            @NotBlank(message = "Informe o usuário.") String username,
            @NotBlank(message = "Informe a senha.") String password) {}

    public record LoginResponse(String token, Long userId, String username, String displayName, List<String> permissions) {}
}
