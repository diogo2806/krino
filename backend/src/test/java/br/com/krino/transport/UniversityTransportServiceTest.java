package br.com.krino.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.security.AuthorizationService;

class UniversityTransportServiceTest {

    private UniversityTransportService service;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityAuditService auditService = mock(SecurityAuditService.class);
        authentication = mock(Authentication.class);
        when(authorizationService.hasPermission(authentication, "TRANSPORT_REQUEST_WRITE")).thenReturn(true);
        service = new UniversityTransportService(jdbcTemplate, authorizationService, auditService);
    }

    @Test
    void exigePeloMenosUmDiaDeTransporte() {
        var input = validInput(List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> service.create(input, authentication));

        assertEquals("Selecione pelo menos um dia da semana para uso do transporte.", error.getMessage());
    }

    @Test
    void rejeitaDataDeNascimentoFutura() {
        var input = new UniversityTransportService.RequestInput(
                "Estudante Teste", "DOC-123", LocalDate.now().plusDays(1), "81999999999",
                "UNIVERSITY", "Pedagogia", "Instituição Teste", List.of("MONDAY"));

        var error = assertThrows(IllegalArgumentException.class, () -> service.create(input, authentication));

        assertEquals("Informe uma data de nascimento válida.", error.getMessage());
    }

    @Test
    void rejeitaTipoDeCursoForaDoEscopoDoRequisito() {
        var input = new UniversityTransportService.RequestInput(
                "Estudante Teste", "DOC-123", LocalDate.of(2000, 1, 1), "81999999999",
                "OTHER", "Curso Teste", "Instituição Teste", List.of("MONDAY"));

        var error = assertThrows(IllegalArgumentException.class, () -> service.create(input, authentication));

        assertEquals("Selecione o tipo de curso: profissionalizante, técnico ou universitário.", error.getMessage());
    }

    @Test
    void rejeitaDiaDaSemanaDesconhecido() {
        var input = validInput(List.of("HOLIDAY"));

        var error = assertThrows(IllegalArgumentException.class, () -> service.create(input, authentication));

        assertEquals("Um dos dias informados é inválido.", error.getMessage());
    }

    private UniversityTransportService.RequestInput validInput(List<String> days) {
        return new UniversityTransportService.RequestInput(
                "Estudante Teste", "DOC-123", LocalDate.of(2000, 1, 1), "81999999999",
                "UNIVERSITY", "Pedagogia", "Instituição Teste", days);
    }
}
