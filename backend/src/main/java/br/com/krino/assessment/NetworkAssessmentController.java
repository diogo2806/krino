package br.com.krino.assessment;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.krino.assessment.NetworkAssessmentService.AnswerSheetImportRequest;
import br.com.krino.assessment.NetworkAssessmentService.ArtifactView;
import br.com.krino.assessment.NetworkAssessmentService.AssessmentRequest;
import br.com.krino.assessment.NetworkAssessmentService.AssessmentView;
import br.com.krino.assessment.NetworkAssessmentService.AssignmentView;
import br.com.krino.assessment.NetworkAssessmentService.AttendanceRequest;
import br.com.krino.assessment.NetworkAssessmentService.CatalogView;
import br.com.krino.assessment.NetworkAssessmentService.ImportSummary;
import br.com.krino.assessment.NetworkAssessmentService.OccurrenceRequest;
import br.com.krino.assessment.NetworkAssessmentService.OccurrenceView;
import br.com.krino.assessment.NetworkAssessmentService.OrganizationSummary;
import br.com.krino.assessment.NetworkAssessmentService.OrganizeRequest;
import br.com.krino.assessment.NetworkAssessmentService.ProcessingRunView;
import br.com.krino.assessment.NetworkAssessmentService.QuestionRequest;
import br.com.krino.assessment.NetworkAssessmentService.QuestionView;
import br.com.krino.assessment.NetworkAssessmentService.ResultSummaryRow;
import br.com.krino.assessment.NetworkAssessmentService.SkillSummaryRow;
import br.com.krino.assessment.NetworkAssessmentService.ValidationSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping("/api/assessments")
public class NetworkAssessmentController {

    private final NetworkAssessmentService service;

    public NetworkAssessmentController(NetworkAssessmentService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_READ')")
    public CatalogView catalog(Authentication authentication) {
        return service.catalog(authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_READ')")
    public List<AssessmentView> list(@RequestParam(required = false) Integer academicYear,
            @RequestParam(required = false) String stage, @RequestParam(required = false) Long schoolId,
            Authentication authentication) {
        return service.list(academicYear, stage, schoolId, authentication);
    }

    @GetMapping("/{assessmentId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_READ')")
    public AssessmentView get(@PathVariable long assessmentId, Authentication authentication) {
        return service.get(assessmentId, authentication);
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_WRITE')")
    public AssessmentView create(@Valid @RequestBody AssessmentRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @GetMapping("/{assessmentId}/questions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_READ')")
    public List<QuestionView> questions(@PathVariable long assessmentId, Authentication authentication) {
        return service.questions(assessmentId, authentication);
    }

    @PutMapping("/{assessmentId}/questions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_WRITE')")
    public List<QuestionView> replaceQuestions(@PathVariable long assessmentId,
            @RequestBody @NotEmpty List<@Valid QuestionRequest> questions, Authentication authentication) {
        return service.replaceQuestions(assessmentId, questions, authentication);
    }

    @PostMapping("/{assessmentId}/organization")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_WRITE')")
    public OrganizationSummary organize(@PathVariable long assessmentId, @Valid @RequestBody OrganizeRequest request,
            Authentication authentication) {
        return service.organize(assessmentId, request, authentication);
    }

    @GetMapping("/{assessmentId}/assignments")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_READ')")
    public List<AssignmentView> assignments(@PathVariable long assessmentId,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            Authentication authentication) {
        return service.assignments(assessmentId, schoolId, classId, authentication);
    }

    @PatchMapping("/{assessmentId}/assignments/{assignmentId}/attendance")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_WRITE')")
    public AssignmentView updateAttendance(@PathVariable long assessmentId, @PathVariable long assignmentId,
            @Valid @RequestBody AttendanceRequest request, Authentication authentication) {
        return service.updateAttendance(assessmentId, assignmentId, request, authentication);
    }

    @GetMapping("/{assessmentId}/artifacts/{type}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_WRITE')")
    public ArtifactView artifact(@PathVariable long assessmentId, @PathVariable String type,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            Authentication authentication) {
        return service.artifact(assessmentId, type, schoolId, classId, authentication);
    }

    @PostMapping("/{assessmentId}/occurrences")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_WRITE')")
    public OccurrenceView recordOccurrence(@PathVariable long assessmentId, @Valid @RequestBody OccurrenceRequest request,
            Authentication authentication) {
        return service.recordOccurrence(assessmentId, request, authentication);
    }

    @GetMapping("/{assessmentId}/occurrences")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_READ')")
    public List<OccurrenceView> occurrences(@PathVariable long assessmentId, Authentication authentication) {
        return service.occurrences(assessmentId, authentication);
    }

    @PostMapping("/{assessmentId}/answer-sheets")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_WRITE')")
    public ImportSummary importAnswerSheets(@PathVariable long assessmentId, @Valid @RequestBody AnswerSheetImportRequest request,
            Authentication authentication) {
        return service.importAnswerSheets(assessmentId, request, authentication);
    }

    @GetMapping("/{assessmentId}/validation")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_PROCESS')")
    public ValidationSummary validation(@PathVariable long assessmentId, Authentication authentication) {
        return service.validation(assessmentId, authentication);
    }

    @PostMapping("/{assessmentId}/process")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_PROCESS')")
    public ProcessingRunView process(@PathVariable long assessmentId, Authentication authentication) {
        return service.process(assessmentId, authentication);
    }

    @GetMapping("/{assessmentId}/processing-runs")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_READ')")
    public List<ProcessingRunView> processingHistory(@PathVariable long assessmentId, Authentication authentication) {
        return service.processingHistory(assessmentId, authentication);
    }

    @GetMapping("/{assessmentId}/results")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_RESULT_READ')")
    public List<ResultSummaryRow> results(@PathVariable long assessmentId, @RequestParam(defaultValue = "NETWORK") String level,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long studentId, Authentication authentication) {
        return service.results(assessmentId, level, schoolId, classId, studentId, authentication);
    }

    @GetMapping("/{assessmentId}/results/skills")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_RESULT_READ')")
    public List<SkillSummaryRow> skillResults(@PathVariable long assessmentId,
            @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long studentId, Authentication authentication) {
        return service.skillResults(assessmentId, schoolId, classId, studentId, authentication);
    }
}
