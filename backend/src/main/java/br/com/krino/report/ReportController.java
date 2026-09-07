package br.com.krino.report;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.krino.report.AssessmentReportService.AlternativeRow;
import br.com.krino.report.AssessmentReportService.BreakdownRow;
import br.com.krino.report.AssessmentReportService.ComponentRow;
import br.com.krino.report.AssessmentReportService.DashboardView;
import br.com.krino.report.AssessmentReportService.InterventionProfile;
import br.com.krino.report.AssessmentReportService.PerformanceLevel;
import br.com.krino.report.AssessmentReportService.QuestionRow;
import br.com.krino.report.AssessmentReportService.ReportContext;
import br.com.krino.report.AssessmentReportService.SchoolSkillRow;
import br.com.krino.report.AssessmentReportService.SkillRow;
import br.com.krino.report.AssessmentReportService.StudentAnswerRow;
import br.com.krino.report.PerformanceLevelService.PerformanceLevelRequest;
import br.com.krino.report.ReportExportService.ExportFile;
import br.com.krino.report.ReportFilterService.ReportFilters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@Validated
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final AssessmentReportService reportService;
    private final ReportFilterService filterService;
    private final PerformanceLevelService performanceLevelService;
    private final ReportExportService exportService;

    public ReportController(AssessmentReportService reportService, ReportFilterService filterService,
            PerformanceLevelService performanceLevelService, ReportExportService exportService) {
        this.reportService = reportService;
        this.filterService = filterService;
        this.performanceLevelService = performanceLevelService;
        this.exportService = exportService;
    }

    @GetMapping("/context")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public ReportContext context(@RequestParam(required = false) Integer year, Authentication authentication) {
        return reportService.context(year, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/filters")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public ReportFilters filters(@PathVariable long assessmentId, @RequestParam(required = false) Long schoolId,
            Authentication authentication) {
        return filterService.filters(assessmentId, schoolId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/dashboard")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public DashboardView dashboard(@PathVariable long assessmentId, @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classId, Authentication authentication) {
        return reportService.dashboard(assessmentId, schoolId, classId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/school-skills")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public List<SchoolSkillRow> schoolSkills(@PathVariable long assessmentId, @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classId, Authentication authentication) {
        return reportService.schoolSkills(assessmentId, schoolId, classId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/alternatives")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public List<AlternativeRow> alternatives(@PathVariable long assessmentId, @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classId, @RequestParam(required = false) Long studentId,
            Authentication authentication) {
        return reportService.alternatives(assessmentId, schoolId, classId, studentId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/questions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public List<QuestionRow> questions(@PathVariable long assessmentId, @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classId, @RequestParam(required = false) Long studentId,
            Authentication authentication) {
        return reportService.questions(assessmentId, schoolId, classId, studentId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/skills")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public List<SkillRow> skills(@PathVariable long assessmentId, @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classId, @RequestParam(required = false) Long studentId,
            Authentication authentication) {
        return reportService.skills(assessmentId, schoolId, classId, studentId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/components")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public List<ComponentRow> components(@PathVariable long assessmentId, @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classId, Authentication authentication) {
        return reportService.components(assessmentId, schoolId, classId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/students/{studentId}/answers")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public List<StudentAnswerRow> studentAnswers(@PathVariable long assessmentId, @PathVariable long studentId,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            Authentication authentication) {
        filterService.requireStudentVisible(assessmentId, studentId, schoolId, classId, authentication);
        return reportService.studentAnswers(assessmentId, studentId, schoolId, classId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/students/{studentId}/intervention")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public InterventionProfile intervention(@PathVariable long assessmentId, @PathVariable long studentId,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            Authentication authentication) {
        filterService.requireStudentVisible(assessmentId, studentId, schoolId, classId, authentication);
        return reportService.intervention(assessmentId, studentId, schoolId, classId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/participation")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public List<BreakdownRow> participation(@PathVariable long assessmentId, @RequestParam(defaultValue = "SCHOOL") String level,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            Authentication authentication) {
        return reportService.participation(assessmentId, level, schoolId, classId, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/performance-levels")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_READ')")
    public List<PerformanceLevel> performanceLevels(@PathVariable long assessmentId) {
        return performanceLevelService.list(assessmentId);
    }

    @PutMapping("/assessments/{assessmentId}/performance-levels")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_WRITE')")
    public List<PerformanceLevel> replacePerformanceLevels(@PathVariable long assessmentId,
            @RequestBody @NotEmpty List<@Valid PerformanceLevelRequest> requests, Authentication authentication) {
        return performanceLevelService.replace(assessmentId, requests, authentication);
    }

    @GetMapping("/assessments/{assessmentId}/export")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'REPORT_EXPORT')")
    public ResponseEntity<byte[]> export(@PathVariable long assessmentId, @RequestParam String report,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long studentId, Authentication authentication) {
        ExportFile file = exportService.export(assessmentId, report, schoolId, classId, studentId, authentication);
        ContentDisposition disposition = ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
