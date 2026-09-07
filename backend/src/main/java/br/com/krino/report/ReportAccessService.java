package br.com.krino.report;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.secretaria.SchoolAccessService;
import br.com.krino.security.AuthorizationService;

@Service
public class ReportAccessService {

    private final AuthorizationService authorizationService;
    private final SchoolAccessService schoolAccessService;

    public ReportAccessService(AuthorizationService authorizationService, SchoolAccessService schoolAccessService) {
        this.authorizationService = authorizationService;
        this.schoolAccessService = schoolAccessService;
    }

    public boolean hasNetworkRead(Authentication authentication) {
        return authorizationService.hasNetworkPermission(authentication, "REPORT_READ");
    }

    public boolean hasNetworkExport(Authentication authentication) {
        return authorizationService.hasNetworkPermission(authentication, "REPORT_EXPORT");
    }

    public List<Long> readableSchoolIds(Authentication authentication) {
        return schoolAccessService.accessibleSchoolIds(authentication, "REPORT_READ");
    }

    public List<Long> exportableSchoolIds(Authentication authentication) {
        return schoolAccessService.accessibleSchoolIds(authentication, "REPORT_EXPORT");
    }

    public void requireRead(Authentication authentication, Long schoolId) {
        if (schoolId == null) {
            if (!hasNetworkRead(authentication)) throw new AccessDeniedException("A visão consolidada da Rede exige permissão municipal de relatórios.");
            return;
        }
        String schoolCode = schoolAccessService.schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "REPORT_READ", schoolCode)) {
            throw new AccessDeniedException("Sua conta não possui acesso aos relatórios desta unidade escolar.");
        }
    }

    public void requireExport(Authentication authentication, Long schoolId) {
        if (schoolId == null) {
            if (!hasNetworkExport(authentication)) throw new AccessDeniedException("A exportação consolidada da Rede exige permissão municipal.");
            return;
        }
        String schoolCode = schoolAccessService.schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "REPORT_EXPORT", schoolCode)) {
            throw new AccessDeniedException("Sua conta não possui permissão para exportar dados desta unidade escolar.");
        }
    }
}
