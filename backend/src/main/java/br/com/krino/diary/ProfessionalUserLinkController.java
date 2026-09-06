package br.com.krino.diary;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.secretaria.SchoolAccessService;
import br.com.krino.secretaria.SecretariaRegistryService;

@RestController
@RequestMapping("/api/secretaria/professionals/{professionalId}/user-link")
public class ProfessionalUserLinkController {

    private final JdbcTemplate jdbcTemplate;
    private final SecretariaRegistryService registryService;
    private final SchoolAccessService schoolAccessService;
    private final SecurityAuditService auditService;

    public ProfessionalUserLinkController(JdbcTemplate jdbcTemplate, SecretariaRegistryService registryService,
                                          SchoolAccessService schoolAccessService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.registryService = registryService;
        this.schoolAccessService = schoolAccessService;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<UserLinkView> get(@PathVariable long professionalId, Authentication authentication) {
        var professional = registryService.getProfessional(professionalId);
        schoolAccessService.requireRead(authentication, professional.schoolId());
        List<UserLinkView> links = jdbcTemplate.query(
                "select u.id user_id, u.username, u.display_name from professional_user_account l join app_user u on u.id = l.user_id where l.professional_id = ?",
                (rs, rowNum) -> new UserLinkView(rs.getLong("user_id"), rs.getString("username"), rs.getString("display_name")), professionalId);
        return links.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(links.getFirst());
    }

    @PutMapping
    @Transactional
    public UserLinkView link(@PathVariable long professionalId, @Valid @RequestBody UserLinkRequest request, Authentication authentication) {
        var professional = registryService.getProfessional(professionalId);
        schoolAccessService.requireWrite(authentication, professional.schoolId());
        List<UserCore> users = jdbcTemplate.query("select id, username, display_name, active from app_user where lower(username) = lower(?)",
                (rs, rowNum) -> new UserCore(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getBoolean("active")), request.username().trim());
        if (users.isEmpty()) throw new IllegalArgumentException("Usuário não encontrado. Informe o usuário utilizado no login.");
        UserCore user = users.getFirst();
        if (!user.active()) throw new IllegalArgumentException("O usuário informado está inativo.");
        Integer linkedElsewhere = jdbcTemplate.queryForObject("select count(*) from professional_user_account where user_id = ? and professional_id <> ?", Integer.class, user.id(), professionalId);
        if (linkedElsewhere != null && linkedElsewhere > 0) throw new IllegalArgumentException("Este usuário já está vinculado a outro profissional da educação.");
        jdbcTemplate.update("insert into professional_user_account (professional_id, user_id) values (?, ?) on conflict (professional_id) do update set user_id = excluded.user_id", professionalId, user.id());
        auditService.record(authentication.getName(), "PROFESSIONAL_USER_LINKED", "PROFESSIONAL", Long.toString(professionalId), "Conta vinculada ao profissional: " + user.username());
        return new UserLinkView(user.id(), user.username(), user.displayName());
    }

    @DeleteMapping
    @Transactional
    public void unlink(@PathVariable long professionalId, Authentication authentication) {
        var professional = registryService.getProfessional(professionalId);
        schoolAccessService.requireWrite(authentication, professional.schoolId());
        jdbcTemplate.update("delete from professional_user_account where professional_id = ?", professionalId);
        auditService.record(authentication.getName(), "PROFESSIONAL_USER_UNLINKED", "PROFESSIONAL", Long.toString(professionalId), "Conta removida do profissional da educação.");
    }

    private record UserCore(Long id, String username, String displayName, boolean active) {}
    public record UserLinkRequest(@NotBlank(message = "Informe o usuário utilizado no login.") String username) {}
    public record UserLinkView(Long userId, String username, String displayName) {}
}
