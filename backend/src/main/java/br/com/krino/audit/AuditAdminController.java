package br.com.krino.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AuditAdminController {

    private final AuditQueryService auditQueryService;
    private final AdministrationDataExportService dataExportService;
    private final SecurityAuditService securityAuditService;

    public AuditAdminController(
            AuditQueryService auditQueryService,
            AdministrationDataExportService dataExportService,
            SecurityAuditService securityAuditService) {
        this.auditQueryService = auditQueryService;
        this.dataExportService = dataExportService;
        this.securityAuditService = securityAuditService;
    }

    @GetMapping("/audit")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'AUDIT_READ')")
    public List<AuditQueryService.AuditEventView> audit(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer limit) {
        return auditQueryService.search(from, to, actor, action, limit);
    }

    @GetMapping("/data-export")
    @PreAuthorize("@authorizationService.hasNetworkPermission(authentication, 'DATA_EXPORT')")
    public AdministrationDataExportService.AdministrationExport export(Authentication authentication) {
        AdministrationDataExportService.AdministrationExport export = dataExportService.export();
        securityAuditService.record(
                authentication.getName(),
                "ADMINISTRATION_DATA_EXPORTED",
                "ADMINISTRATION_DATA",
                null,
                "Exportação administrativa JSON gerada; credenciais, senhas, tokens e segredos foram omitidos.");
        return export;
    }
}
