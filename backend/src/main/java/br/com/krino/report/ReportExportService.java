package br.com.krino.report;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import br.com.krino.report.AssessmentReportService.AlternativeRow;
import br.com.krino.report.AssessmentReportService.BreakdownRow;
import br.com.krino.report.AssessmentReportService.ComponentRow;
import br.com.krino.report.AssessmentReportService.InterventionProfile;
import br.com.krino.report.AssessmentReportService.QuestionRow;
import br.com.krino.report.AssessmentReportService.SchoolSkillRow;
import br.com.krino.report.AssessmentReportService.SkillRow;
import br.com.krino.report.AssessmentReportService.StudentAnswerRow;

@Service
public class ReportExportService {

    private final AssessmentReportService reportService;
    private final ReportAccessService accessService;
    private final CsvWriter csvWriter;

    public ReportExportService(AssessmentReportService reportService, ReportAccessService accessService, CsvWriter csvWriter) {
        this.reportService = reportService;
        this.accessService = accessService;
        this.csvWriter = csvWriter;
    }

    public ExportFile export(long assessmentId, String report, Long schoolId, Long classId, Long studentId,
            Authentication authentication) {
        accessService.requireExport(authentication, schoolId);
        String normalized = report == null ? "" : report.trim().toUpperCase(Locale.ROOT);
        List<List<?>> rows = new ArrayList<>();
        String suffix;
        switch (normalized) {
            case "SCHOOL_SKILLS" -> {
                suffix = "habilidades-por-escola";
                rows.add(List.of("Escola", "Descritor", "Habilidade", "Acertos", "Base de questões", "Percentual de acerto", "Nível de desempenho"));
                for (SchoolSkillRow row : reportService.schoolSkills(assessmentId, schoolId, classId, authentication)) {
                    rows.add(List.of(row.schoolName(), row.descriptor(), row.skill(), row.correctAnswers(), row.totalQuestions(), decimal(row.correctPercent()), row.performanceLevel()));
                }
            }
            case "ALTERNATIVES" -> {
                suffix = "respostas-por-alternativa";
                rows.add(List.of("Questão", "Descritor", "Habilidade", "Alternativa correta", "Alternativa marcada", "Respostas", "Base de respostas", "Percentual de respostas"));
                for (AlternativeRow row : reportService.alternatives(assessmentId, schoolId, classId, studentId, authentication)) {
                    rows.add(List.of(row.sequenceNumber(), row.descriptor(), row.skill(), row.correctOption(), row.selectedOption(), row.responses(), row.baseResponses(), decimal(row.responsePercent())));
                }
            }
            case "QUESTIONS" -> {
                suffix = "acerto-por-questao";
                rows.add(List.of("Questão", "Descritor", "Habilidade", "Acertos", "Base de respostas", "Percentual de acerto", "Complexidade relativa"));
                for (QuestionRow row : reportService.questions(assessmentId, schoolId, classId, studentId, authentication)) {
                    rows.add(List.of(row.sequenceNumber(), row.descriptor(), row.skill(), row.correctAnswers(), row.responses(), decimal(row.correctPercent()), row.relativeComplexity()));
                }
            }
            case "SKILLS" -> {
                suffix = "acerto-por-habilidade";
                rows.add(List.of("Descritor", "Habilidade", "Acertos", "Base de questões", "Percentual de acerto", "Nível de desempenho"));
                for (SkillRow row : reportService.skills(assessmentId, schoolId, classId, studentId, authentication)) {
                    rows.add(List.of(row.descriptor(), row.skill(), row.correctAnswers(), row.totalQuestions(), decimal(row.correctPercent()), row.performanceLevel()));
                }
            }
            case "COMPONENTS" -> {
                suffix = "analise-por-componente";
                rows.add(List.of("Componente curricular", "Estudantes", "Acertos", "Base de questões", "Percentual de acerto"));
                for (ComponentRow row : reportService.components(assessmentId, schoolId, classId, authentication)) {
                    rows.add(List.of(row.componentName(), row.students(), row.correctAnswers(), row.totalQuestions(), decimal(row.correctPercent())));
                }
            }
            case "PARTICIPATION" -> {
                suffix = "participacao";
                rows.add(List.of("Nível", "Participantes", "Base de estudantes", "Percentual de participação"));
                for (BreakdownRow row : reportService.participation(assessmentId, classId == null ? "SCHOOL" : "CLASS", schoolId, classId, authentication)) {
                    rows.add(List.of(row.label(), row.participants(), row.expectedStudents(), decimal(row.percentage())));
                }
            }
            case "STUDENT_ANSWERS" -> {
                if (studentId == null) throw new IllegalArgumentException("Selecione um estudante para exportar as respostas individuais.");
                suffix = "respostas-do-estudante";
                rows.add(List.of("Questão", "Descritor", "Habilidade", "Resposta do estudante", "Resposta correta", "Resultado"));
                for (StudentAnswerRow row : reportService.studentAnswers(assessmentId, studentId, schoolId, classId, authentication)) {
                    rows.add(List.of(row.sequenceNumber(), row.descriptor(), row.skill(), row.selectedOption(), row.correctOption(), row.correct() ? "Acerto" : "Erro"));
                }
            }
            case "INTERVENTION" -> {
                if (studentId == null) throw new IllegalArgumentException("Selecione um estudante para exportar o perfil de intervenção.");
                suffix = "intervencao-pedagogica";
                InterventionProfile profile = reportService.intervention(assessmentId, studentId, schoolId, classId, authentication);
                rows.add(List.of("Estudante", "Matrícula", "Percentual geral de acerto", "Nível de desempenho"));
                rows.add(List.of(profile.studentName(), profile.registration(), decimal(profile.correctPercent()), profile.performanceLevel()));
                rows.add(List.of());
                rows.add(List.of("Grupo", "Descritor", "Habilidade", "Percentual de acerto", "Nível de desempenho"));
                for (SkillRow row : profile.strengths()) rows.add(List.of("Ponto forte relativo", row.descriptor(), row.skill(), decimal(row.correctPercent()), row.performanceLevel()));
                for (SkillRow row : profile.attentionPriorities()) rows.add(List.of("Prioridade relativa de atenção", row.descriptor(), row.skill(), decimal(row.correctPercent()), row.performanceLevel()));
            }
            default -> throw new IllegalArgumentException("Relatório inválido para exportação.");
        }
        byte[] content = csvWriter.write(rows).getBytes(StandardCharsets.UTF_8);
        return new ExportFile("avaliacao-%d-%s.csv".formatted(assessmentId, suffix), "text/csv; charset=UTF-8", content);
    }

    private String decimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    public record ExportFile(String fileName, String contentType, byte[] content) {}
}
