package br.com.krino.assessment;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.secretaria.SchoolAccessService;
import br.com.krino.security.AuthorizationService;

@Service
public class AssessmentAccessService {

    private final AuthorizationService authorizationService;
    private final SchoolAccessService schoolAccessService;

    public AssessmentAccessService(AuthorizationService authorizationService, SchoolAccessService schoolAccessService) {
        this.authorizationService = authorizationService;
        this.schoolAccessService = schoolAccessService;
    }

    public void requireNetworkWrite(Authentication authentication) {
        requireNetwork(authentication, "ASSESSMENT_WRITE", "A parametrização da Avaliação em Rede exige permissão municipal.");
    }

    public void requireNetworkProcess(Authentication authentication) {
        requireNetwork(authentication, "ASSESSMENT_PROCESS", "O processamento da Avaliação em Rede exige permissão municipal.");
    }

    public void requireResultRead(Authentication authentication, Long schoolId) {
        if (schoolId == null) {
            requireNetwork(authentication, "ASSESSMENT_RESULT_READ", "A consolidação da Rede exige permissão municipal.");
            return;
        }
        String schoolCode = schoolAccessService.schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "ASSESSMENT_RESULT_READ", schoolCode)) {
            throw new AccessDeniedException("Sua conta não possui acesso aos resultados desta unidade escolar.");
        }
    }

    public void requireRead(Authentication authentication, Long schoolId) {
        if (schoolId == null) {
            if (!authorizationService.hasNetworkPermission(authentication, "ASSESSMENT_READ")) {
                throw new AccessDeniedException("Selecione uma unidade escolar autorizada para consultar avaliações.");
            }
            return;
        }
        String schoolCode = schoolAccessService.schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "ASSESSMENT_READ", schoolCode)) {
            throw new AccessDeniedException("Sua conta não possui acesso às avaliações desta unidade escolar.");
        }
    }

    public List<Long> readableSchoolIds(Authentication authentication) {
        return schoolAccessService.accessibleSchoolIds(authentication, "ASSESSMENT_READ");
    }

    public List<Long> resultSchoolIds(Authentication authentication) {
        return schoolAccessService.accessibleSchoolIds(authentication, "ASSESSMENT_RESULT_READ");
    }

    public boolean hasNetworkRead(Authentication authentication) {
        return authorizationService.hasNetworkPermission(authentication, "ASSESSMENT_READ");
    }

    public boolean hasNetworkResultRead(Authentication authentication) {
        return authorizationService.hasNetworkPermission(authentication, "ASSESSMENT_RESULT_READ");
    }

    private void requireNetwork(Authentication authentication, String permission, String message) {
        if (!authorizationService.hasNetworkPermission(authentication, permission)) {
            throw new AccessDeniedException(message);
        }
    }
}
