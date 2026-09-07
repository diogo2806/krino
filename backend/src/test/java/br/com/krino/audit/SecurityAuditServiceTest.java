package br.com.krino.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SecurityAuditServiceTest {

    private final SecurityAuditService service = new SecurityAuditService(mock(JdbcTemplate.class));

    @Test
    void shouldRedactPasswordsTokensSecretsAndCredentials() {
        String input = "senha=abc123 token:xyz secret valor credential='cred-1'";

        String sanitized = service.sanitizeDetails(input);

        assertFalse(sanitized.contains("abc123"));
        assertFalse(sanitized.contains("xyz"));
        assertFalse(sanitized.contains("valor"));
        assertFalse(sanitized.contains("cred-1"));
        assertEquals("senha=[REDACTED] token=[REDACTED] secret=[REDACTED] credential=[REDACTED]'", sanitized);
    }

    @Test
    void shouldRedactAuthorizationAndBearerTokens() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1Ni.fake.signature; Bearer standalone-token";

        String sanitized = service.sanitizeDetails(input);

        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1Ni.fake.signature"));
        assertFalse(sanitized.contains("standalone-token"));
        assertEquals("Authorization=[REDACTED]; Bearer [REDACTED]", sanitized);
    }

    @Test
    void shouldLimitDetailsToDatabaseColumnLength() {
        String sanitized = service.sanitizeDetails("a".repeat(1200));

        assertEquals(1000, sanitized.length());
    }
}
