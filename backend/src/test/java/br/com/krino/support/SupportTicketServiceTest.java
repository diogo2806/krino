package br.com.krino.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.security.AuthorizationService;
import br.com.krino.security.KrinoUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class SupportTicketServiceTest {

    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final SupportTicketService service = new SupportTicketService(
            mock(JdbcTemplate.class),
            authorizationService,
            mock(SecurityAuditService.class));

    @Test
    void shouldUseDocumentedResponseTargets() {
        assertEquals(1, service.responseTargetHours(SupportTicketService.Severity.CRITICAL));
        assertEquals(4, service.responseTargetHours(SupportTicketService.Severity.MEDIUM));
        assertEquals(24, service.responseTargetHours(SupportTicketService.Severity.LOW));
    }

    @Test
    void shouldUseDocumentedSolutionTargets() {
        assertEquals(4, service.solutionTargetHours(SupportTicketService.Severity.CRITICAL));
        assertEquals(24, service.solutionTargetHours(SupportTicketService.Severity.MEDIUM));
        assertEquals(72, service.solutionTargetHours(SupportTicketService.Severity.LOW));
    }

    @Test
    void shouldAllowRequesterToReadOwnTicket() {
        Authentication authentication = authentication(10L);

        assertDoesNotThrow(() -> service.requireTicketAccess(ticket(10L, SupportTicketService.TicketStatus.OPEN), authentication));
    }

    @Test
    void shouldRejectRequesterTryingToReadAnotherUsersTicket() {
        Authentication authentication = authentication(10L);
        when(authorizationService.hasNetworkPermission(authentication, "SUPPORT_TICKET_MANAGE")).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.requireTicketAccess(ticket(99L, SupportTicketService.TicketStatus.OPEN), authentication));
    }

    @Test
    void shouldAllowManagerToReadAnotherUsersTicket() {
        Authentication authentication = authentication(10L);
        when(authorizationService.hasNetworkPermission(authentication, "SUPPORT_TICKET_MANAGE")).thenReturn(true);

        assertDoesNotThrow(() -> service.requireTicketAccess(ticket(99L, SupportTicketService.TicketStatus.OPEN), authentication));
    }

    @Test
    void shouldRequireResolutionBeforeResolvingOrClosing() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateManagement(SupportTicketService.TicketStatus.OPEN, SupportTicketService.TicketStatus.RESOLVED, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateManagement(SupportTicketService.TicketStatus.IN_PROGRESS, SupportTicketService.TicketStatus.CLOSED, null));
        assertDoesNotThrow(
                () -> service.validateManagement(SupportTicketService.TicketStatus.IN_PROGRESS, SupportTicketService.TicketStatus.RESOLVED, "Correção aplicada."));
    }

    @Test
    void shouldKeepClosedTicketReadOnly() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateManagement(SupportTicketService.TicketStatus.CLOSED, SupportTicketService.TicketStatus.OPEN, null));
    }

    @Test
    void shouldRegisterFirstResponseOnlyForFirstSupportInteraction() {
        OffsetDateTime existingResponse = OffsetDateTime.now();

        assertTrue(service.shouldRegisterFirstSupportResponse(true, null));
        assertFalse(service.shouldRegisterFirstSupportResponse(false, null));
        assertFalse(service.shouldRegisterFirstSupportResponse(true, existingResponse));
    }

    private Authentication authentication(long userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new KrinoUserPrincipal(
                userId, "user-" + userId, "", "Usuário " + userId, true, List.of()));
        return authentication;
    }

    private SupportTicketService.TicketView ticket(long ownerId, SupportTicketService.TicketStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new SupportTicketService.TicketView(
                1L,
                ownerId,
                "requester",
                "Solicitante",
                "Assunto",
                "Descrição",
                SupportTicketService.Severity.LOW,
                status,
                null,
                now,
                null,
                null,
                null,
                now,
                24,
                72,
                false);
    }
}
