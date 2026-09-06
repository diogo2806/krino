package br.com.krino.evaluation;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class NetworkEvaluationAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public NetworkEvaluationAccessService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    public void requireNetworkRead(Authentication authentication) {
        if (!(authorizationService.hasNetworkPermission(authentication, "EVALUATION_READ")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_MANAGE")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_PROCESS")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_RESULT_READ"))) {
            throw new AccessDeniedException("A consulta municipal de avaliações exige permissão de Rede.");
        }
    }

    public void requireNetworkManage(Authentication authentication) {
        if (!authorizationService.hasNetworkPermission(authentication, "EVALUATION_MANAGE")) {
            throw new AccessDeniedException("A gestão das avaliações exige permissão municipal.");
        }
    }

    public void requireNetworkProcess(Authentication authentication) {
        if (!authorizationService.hasNetworkPermission(authentication, "EVALUATION_PROCESS")) {
            throw new AccessDeniedException("O processamento de gabaritos exige permissão municipal.");
        }
    }

    public void requireResultRead(Authentication authentication, Long schoolId) {
        if (schoolId == null) {
            if (!authorizationService.hasNetworkPermission(authentication, "EVALUATION_RESULT_READ")) {
                throw new AccessDeniedException("A consolidação municipal exige permissão de resultados em escopo de Rede.");
            }
            return;
        }
        String schoolCode = schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "EVALUATION_RESULT_READ", schoolCode)) {
            throw new AccessDeniedException("Sua conta não possui permissão para consultar resultados desta unidade escolar.");
        }
    }

    public void requireEvaluationRead(Authentication authentication, long evaluationId) {
        if (authorizationService.hasNetworkPermission(authentication, "EVALUATION_READ")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_MANAGE")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_PROCESS")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_RESULT_READ")) {
            return;
        }
        Long userId = principalId(authentication);
        Integer count = jdbcTemplate.queryForObject(
                "select count(distinct ec.class_id) from network_evaluation_class ec "
                        + "join school_class c on c.id = ec.class_id "
                        + "join school_unit s on s.id = c.school_id "
                        + "join user_role_assignment ura on ura.user_id = ? and ura.scope_type = 'SCHOOL' and ura.scope_reference = s.code "
                        + "join access_role_permission rp on rp.role_id = ura.role_id "
                        + "join access_permission p on p.id = rp.permission_id "
                        + "where ec.evaluation_id = ? and p.code in ('EVALUATION_READ', 'EVALUATION_RESULT_READ')",
                Integer.class, userId, evaluationId);
        if (count == null || count == 0) {
            throw new AccessDeniedException("Sua conta não possui acesso a esta avaliação.");
        }
    }

    public List<Long> accessibleSchoolIds(Authentication authentication) {
        if (authorizationService.hasNetworkPermission(authentication, "EVALUATION_READ")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_RESULT_READ")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_MANAGE")
                || authorizationService.hasNetworkPermission(authentication, "EVALUATION_PROCESS")) {
            return jdbcTemplate.query("select id from school_unit where active = true order by name", (rs, rowNum) -> rs.getLong("id"));
        }
        return jdbcTemplate.query(
                "select distinct s.id from school_unit s "
                        + "join user_role_assignment ura on ura.scope_type = 'SCHOOL' and ura.scope_reference = s.code "
                        + "join access_role_permission rp on rp.role_id = ura.role_id "
                        + "join access_permission p on p.id = rp.permission_id "
                        + "where ura.user_id = ? and p.code in ('EVALUATION_READ', 'EVALUATION_RESULT_READ') and s.active = true order by s.id",
                (rs, rowNum) -> rs.getLong("id"), principalId(authentication));
    }

    private String schoolCode(long schoolId) {
        List<String> rows = jdbcTemplate.query("select code from school_unit where id = ?", (rs, rowNum) -> rs.getString("code"), schoolId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Unidade escolar não encontrada.");
        return rows.getFirst();
    }

    private long principalId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) {
            throw new AccessDeniedException("Usuário autenticado não identificado.");
        }
        return principal.id();
    }
}
