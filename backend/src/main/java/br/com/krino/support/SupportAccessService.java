package br.com.krino.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.secretaria.SchoolAccessService;
import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class SupportAccessService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;
    private final SchoolAccessService schoolAccessService;

    public SupportAccessService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService,
            SchoolAccessService schoolAccessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
        this.schoolAccessService = schoolAccessService;
    }

    public long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) {
            throw new AccessDeniedException("Usuário autenticado não identificado.");
        }
        return principal.id();
    }

    public void requireCreate(Authentication authentication, Long schoolId) {
        if (!authorizationService.hasPermission(authentication, "SUPPORT_TICKET_CREATE")) {
            throw new AccessDeniedException("Sua conta não possui permissão para abrir chamados.");
        }
        if (schoolId == null) return;
        String schoolCode = schoolAccessService.schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "SUPPORT_TICKET_CREATE", schoolCode)) {
            throw new AccessDeniedException("Sua conta não possui acesso à unidade escolar informada.");
        }
    }

    public void requireVisible(long ticketId, Authentication authentication) {
        TicketAccess ticket = ticketAccess(ticketId);
        long userId = currentUserId(authentication);
        if (ticket.createdByUserId() == userId || canManage(ticket.schoolId(), authentication) || canReport(ticket.schoolId(), authentication)) return;
        throw new AccessDeniedException("Sua conta não possui acesso a este chamado.");
    }

    public void requireInteract(long ticketId, Authentication authentication) {
        TicketAccess ticket = ticketAccess(ticketId);
        long userId = currentUserId(authentication);
        if (ticket.createdByUserId() == userId || canManage(ticket.schoolId(), authentication)) return;
        throw new AccessDeniedException("Sua conta não possui permissão para interagir com este chamado.");
    }

    public void requireManage(long ticketId, Authentication authentication) {
        TicketAccess ticket = ticketAccess(ticketId);
        if (canManage(ticket.schoolId(), authentication)) return;
        throw new AccessDeniedException("Sua conta não possui permissão para atender este chamado.");
    }

    public void requireReport(Authentication authentication, Long schoolId) {
        if (schoolId == null) {
            if (!authorizationService.hasNetworkPermission(authentication, "SUPPORT_REPORT_READ")) {
                throw new AccessDeniedException("A visão consolidada de suporte exige permissão municipal.");
            }
            return;
        }
        String schoolCode = schoolAccessService.schoolCode(schoolId);
        if (!authorizationService.hasSchoolPermission(authentication, "SUPPORT_REPORT_READ", schoolCode)) {
            throw new AccessDeniedException("Sua conta não possui acesso ao relatório desta unidade escolar.");
        }
    }

    public void requireSlaManage(Authentication authentication) {
        if (!authorizationService.hasNetworkPermission(authentication, "SUPPORT_SLA_MANAGE")) {
            throw new AccessDeniedException("A configuração do SLA exige permissão administrativa municipal.");
        }
    }

    public boolean canManage(Long schoolId, Authentication authentication) {
        if (authorizationService.hasNetworkPermission(authentication, "SUPPORT_TICKET_MANAGE")) return true;
        if (schoolId == null) return false;
        return authorizationService.hasSchoolPermission(authentication, "SUPPORT_TICKET_MANAGE", schoolAccessService.schoolCode(schoolId));
    }

    public boolean canReport(Long schoolId, Authentication authentication) {
        if (authorizationService.hasNetworkPermission(authentication, "SUPPORT_REPORT_READ")) return true;
        if (schoolId == null) return false;
        return authorizationService.hasSchoolPermission(authentication, "SUPPORT_REPORT_READ", schoolAccessService.schoolCode(schoolId));
    }

    public boolean canManageAny(Authentication authentication) {
        return authorizationService.hasPermission(authentication, "SUPPORT_TICKET_MANAGE");
    }

    public boolean canReportAny(Authentication authentication) {
        return authorizationService.hasPermission(authentication, "SUPPORT_REPORT_READ");
    }

    public boolean canManageSla(Authentication authentication) {
        return authorizationService.hasNetworkPermission(authentication, "SUPPORT_SLA_MANAGE");
    }

    public List<Long> visibleManagementSchoolIds(Authentication authentication) {
        Set<Long> ids = new LinkedHashSet<>();
        if (authorizationService.hasPermission(authentication, "SUPPORT_TICKET_MANAGE")) {
            ids.addAll(schoolAccessService.accessibleSchoolIds(authentication, "SUPPORT_TICKET_MANAGE"));
        }
        if (authorizationService.hasPermission(authentication, "SUPPORT_REPORT_READ")) {
            ids.addAll(schoolAccessService.accessibleSchoolIds(authentication, "SUPPORT_REPORT_READ"));
        }
        return new ArrayList<>(ids);
    }

    public List<Long> creatableSchoolIds(Authentication authentication) {
        return schoolAccessService.accessibleSchoolIds(authentication, "SUPPORT_TICKET_CREATE");
    }

    public boolean hasNetworkManagementVisibility(Authentication authentication) {
        return authorizationService.hasNetworkPermission(authentication, "SUPPORT_TICKET_MANAGE")
                || authorizationService.hasNetworkPermission(authentication, "SUPPORT_REPORT_READ");
    }

    private TicketAccess ticketAccess(long ticketId) {
        List<TicketAccess> rows = jdbcTemplate.query(
                "select created_by_user_id, school_id from support_ticket where id = ?",
                (rs, rowNum) -> new TicketAccess(rs.getLong("created_by_user_id"), (Long) rs.getObject("school_id")), ticketId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Chamado não encontrado.");
        return rows.getFirst();
    }

    private record TicketAccess(long createdByUserId, Long schoolId) {}
}
