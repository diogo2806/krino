package br.com.krino.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SupportTicketServiceTest {

    private final SupportTicketService service = new SupportTicketService(
            mock(JdbcTemplate.class),
            mock(AuthorizationService.class),
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
}
