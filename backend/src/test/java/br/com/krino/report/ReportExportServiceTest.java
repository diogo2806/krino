package br.com.krino.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import br.com.krino.report.AssessmentReportService.BreakdownRow;
import br.com.krino.report.AssessmentReportService.InterventionProfile;

class ReportExportServiceTest {

    @Test
    void shouldExportSchoolParticipationBrokenDownByClassAsCsv() {
        AssessmentReportService reportService = mock(AssessmentReportService.class);
        ReportAccessService accessService = mock(ReportAccessService.class);
        ReportFilterService filterService = mock(ReportFilterService.class);
        Authentication authentication = mock(Authentication.class);
        ReportExportService service = new ReportExportService(reportService, accessService, filterService, new CsvWriter());
        when(reportService.participation(10L, "CLASS", 2L, null, authentication)).thenReturn(List.of(
                new BreakdownRow(7L, "Turma A", 8L, 10L, new BigDecimal("80.00"))));

        ReportExportService.ExportFile file = service.export(10L, "PARTICIPATION", 2L, null, null, authentication);

        assertThat(file.fileName()).isEqualTo("avaliacao-10-participacao.csv");
        assertThat(file.contentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(new String(file.content(), StandardCharsets.UTF_8))
                .contains("Nível", "Turma A", "8", "10", "80.00");
        verify(accessService).requireExport(authentication, 2L);
        verify(reportService).participation(10L, "CLASS", 2L, null, authentication);
    }

    @Test
    void shouldValidateStudentScopeBeforeInterventionExport() {
        AssessmentReportService reportService = mock(AssessmentReportService.class);
        ReportAccessService accessService = mock(ReportAccessService.class);
        ReportFilterService filterService = mock(ReportFilterService.class);
        Authentication authentication = mock(Authentication.class);
        ReportExportService service = new ReportExportService(reportService, accessService, filterService, new CsvWriter());
        when(reportService.intervention(10L, 30L, 2L, 7L, authentication)).thenReturn(
                new InterventionProfile(30L, "MAT-30", "Estudante Teste", new BigDecimal("75.00"), "Adequado", List.of(), List.of()));

        service.export(10L, "INTERVENTION", 2L, 7L, 30L, authentication);

        verify(filterService).requireStudentVisible(10L, 30L, 2L, 7L, authentication);
        verify(reportService).intervention(10L, 30L, 2L, 7L, authentication);
    }
}
