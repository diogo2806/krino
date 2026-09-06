package br.com.krino.evaluation;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/network-evaluations")
public class NetworkEvaluationController {

    private final NetworkEvaluationService service;

    public NetworkEvaluationController(NetworkEvaluationService service) {
        this.service = service;
    }

    @GetMapping("/context")
    public NetworkEvaluationService.EvaluationContext context(Authentication authentication) {
        return service.context(authentication);
    }

    @GetMapping("/classes")
    public List<NetworkEvaluationService.ClassOption> classes(@RequestParam long schoolId, @RequestParam int academicYear, Authentication authentication) {
        return service.classes(schoolId, academicYear, authentication);
    }

    @GetMapping
    public List<NetworkEvaluationService.EvaluationView> list(@RequestParam(required = false) Integer academicYear, @RequestParam(required = false) String stage, Authentication authentication) {
        return service.list(academicYear, stage, authentication);
    }

    @GetMapping("/{evaluationId}")
    public NetworkEvaluationService.EvaluationView get(@PathVariable long evaluationId, Authentication authentication) {
        return service.get(evaluationId, authentication);
    }

    @PostMapping
    public NetworkEvaluationService.EvaluationView create(@RequestBody NetworkEvaluationService.EvaluationInput input, Authentication authentication) {
        return service.create(input, authentication);
    }

    @PutMapping("/{evaluationId}")
    public NetworkEvaluationService.EvaluationView update(@PathVariable long evaluationId, @RequestBody NetworkEvaluationService.EvaluationInput input, Authentication authentication) {
        return service.update(evaluationId, input, authentication);
    }

    @GetMapping("/{evaluationId}/items")
    public List<NetworkEvaluationService.ItemView> items(@PathVariable long evaluationId, Authentication authentication) {
        return service.items(evaluationId, authentication);
    }

    @PostMapping("/{evaluationId}/items")
    public NetworkEvaluationService.ItemView saveItem(@PathVariable long evaluationId, @RequestBody NetworkEvaluationService.ItemInput input, Authentication authentication) {
        return service.addItem(evaluationId, input, authentication);
    }

    @PostMapping("/{evaluationId}/organize")
    public NetworkEvaluationService.OrganizationSummary organize(@PathVariable long evaluationId, Authentication authentication) {
        return service.organize(evaluationId, authentication);
    }

    @GetMapping("/{evaluationId}/students")
    public List<NetworkEvaluationService.StudentView> students(@PathVariable long evaluationId, @RequestParam(required = false) Long schoolId, @RequestParam(required = false) Long classId, Authentication authentication) {
        return service.students(evaluationId, schoolId, classId, authentication);
    }

    @GetMapping("/{evaluationId}/classes/{classId}/materials")
    public NetworkEvaluationService.MaterialsView materials(@PathVariable long evaluationId, @PathVariable long classId, Authentication authentication) {
        return service.materials(evaluationId, classId, authentication);
    }

    @PostMapping("/{evaluationId}/classes/{classId}/occurrences")
    public NetworkEvaluationService.OccurrenceView addOccurrence(@PathVariable long evaluationId, @PathVariable long classId, @RequestBody NetworkEvaluationService.OccurrenceInput input, Authentication authentication) {
        return service.addOccurrence(evaluationId, classId, input, authentication);
    }

    @GetMapping("/{evaluationId}/classes/{classId}/occurrences")
    public List<NetworkEvaluationService.OccurrenceView> occurrences(@PathVariable long evaluationId, @PathVariable long classId, Authentication authentication) {
        return service.occurrences(evaluationId, classId, authentication);
    }

    @PostMapping("/{evaluationId}/students/{evaluationStudentId}/online-access")
    public NetworkEvaluationService.OnlineTokenView issueOnlineAccess(@PathVariable long evaluationId, @PathVariable long evaluationStudentId, @RequestBody NetworkEvaluationService.OnlineTokenInput input, Authentication authentication) {
        return service.issueOnlineToken(evaluationId, evaluationStudentId, input, authentication);
    }

    @PostMapping(value = "/{evaluationId}/answer-batches/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NetworkEvaluationService.BatchView importCsv(@PathVariable long evaluationId, @RequestParam("file") MultipartFile file, Authentication authentication) {
        return service.importCsv(evaluationId, file, authentication);
    }

    @PostMapping("/{evaluationId}/answer-batches/manual")
    public NetworkEvaluationService.BatchView manualAnswers(@PathVariable long evaluationId, @RequestBody NetworkEvaluationService.ManualAnswerInput input, Authentication authentication) {
        return service.addManualAnswers(evaluationId, input, authentication);
    }

    @GetMapping("/{evaluationId}/answer-batches")
    public List<NetworkEvaluationService.BatchView> batches(@PathVariable long evaluationId, Authentication authentication) {
        return service.batches(evaluationId, authentication);
    }

    @GetMapping("/answer-batches/{batchId}/inconsistencies")
    public List<NetworkEvaluationService.InconsistencyView> inconsistencies(@PathVariable long batchId, Authentication authentication) {
        return service.inconsistencies(batchId, authentication);
    }

    @PostMapping("/answer-batches/{batchId}/process")
    public NetworkEvaluationService.ProcessingRunView process(@PathVariable long batchId, Authentication authentication) {
        return service.process(batchId, authentication);
    }

    @GetMapping("/{evaluationId}/results")
    public NetworkEvaluationService.ResultsView results(
            @PathVariable long evaluationId,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) String skillCode,
            @RequestParam(required = false) String descriptorCode,
            Authentication authentication) {
        return service.results(evaluationId, schoolId, classId, studentId, componentId, skillCode, descriptorCode, authentication);
    }

    @GetMapping("/stage-comparison")
    public List<NetworkEvaluationService.StageComparisonView> compareStages(@RequestParam int academicYear, @RequestParam String gradeStage, Authentication authentication) {
        return service.compareStages(academicYear, gradeStage, authentication);
    }
}
