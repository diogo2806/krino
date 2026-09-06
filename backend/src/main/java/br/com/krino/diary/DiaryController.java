package br.com.krino.diary;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diaries")
public class DiaryController {

    private final DiaryService diaryService;
    private final DiaryEvaluationService evaluationService;

    public DiaryController(DiaryService diaryService, DiaryEvaluationService evaluationService) {
        this.diaryService = diaryService;
        this.evaluationService = evaluationService;
    }

    @GetMapping
    public List<DiaryService.DiaryView> list(@RequestParam long classId, Authentication authentication) {
        return diaryService.list(classId, authentication);
    }

    @PostMapping
    public DiaryService.DiaryView create(@Valid @RequestBody DiaryService.CreateDiaryRequest request, Authentication authentication) {
        return diaryService.create(request, authentication);
    }

    @GetMapping("/{diaryId}")
    public DiaryService.DiaryView get(@PathVariable long diaryId, Authentication authentication) {
        return diaryService.get(diaryId, authentication);
    }

    @GetMapping("/{diaryId}/roster")
    public List<DiaryService.RosterStudent> roster(@PathVariable long diaryId, Authentication authentication) {
        return diaryService.roster(diaryId, authentication);
    }

    @GetMapping("/{diaryId}/lessons")
    public List<DiaryService.LessonView> lessons(@PathVariable long diaryId, @RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to, Authentication authentication) {
        return diaryService.lessons(diaryId, from, to, authentication);
    }

    @PutMapping("/{diaryId}/lessons/{date}/{slot}")
    public DiaryService.LessonView saveLesson(@PathVariable long diaryId, @PathVariable LocalDate date, @PathVariable int slot,
                                              @Valid @RequestBody DiaryService.SaveLessonRequest request, Authentication authentication) {
        return diaryService.saveLesson(diaryId, date, slot, request, authentication);
    }

    @GetMapping("/{diaryId}/assessments")
    public List<DiaryEvaluationService.AssessmentView> assessments(@PathVariable long diaryId, Authentication authentication) {
        return evaluationService.assessments(diaryId, authentication);
    }

    @PostMapping("/{diaryId}/assessments")
    public DiaryEvaluationService.AssessmentView createAssessment(@PathVariable long diaryId,
            @Valid @RequestBody DiaryEvaluationService.AssessmentRequest request, Authentication authentication) {
        return evaluationService.createAssessment(diaryId, request, authentication);
    }

    @PutMapping("/{diaryId}/assessments/{assessmentId}")
    public DiaryEvaluationService.AssessmentView updateAssessment(@PathVariable long diaryId, @PathVariable long assessmentId,
            @Valid @RequestBody DiaryEvaluationService.AssessmentRequest request, Authentication authentication) {
        return evaluationService.updateAssessment(diaryId, assessmentId, request, authentication);
    }

    @PutMapping("/{diaryId}/assessments/{assessmentId}/grades")
    public DiaryEvaluationService.AssessmentView saveGrades(@PathVariable long diaryId, @PathVariable long assessmentId,
            @RequestBody List<DiaryEvaluationService.GradeInput> grades, Authentication authentication) {
        return evaluationService.saveGrades(diaryId, assessmentId, grades, authentication);
    }

    @GetMapping("/{diaryId}/planning")
    public List<DiaryEvaluationService.PlanningView> planning(@PathVariable long diaryId, Authentication authentication) {
        return evaluationService.planning(diaryId, authentication);
    }

    @PutMapping("/{diaryId}/planning/{period}")
    public DiaryEvaluationService.PlanningView savePlanning(@PathVariable long diaryId, @PathVariable int period,
            @Valid @RequestBody DiaryEvaluationService.PlanningRequest request, Authentication authentication) {
        return evaluationService.savePlanning(diaryId, period, request, authentication);
    }

    @GetMapping("/{diaryId}/curriculum")
    public List<DiaryEvaluationService.CurriculumItemView> curriculum(@PathVariable long diaryId, Authentication authentication) {
        return evaluationService.curriculum(diaryId, authentication);
    }

    @PostMapping("/{diaryId}/curriculum")
    public DiaryEvaluationService.CurriculumItemView addCurriculum(@PathVariable long diaryId,
            @Valid @RequestBody DiaryEvaluationService.CurriculumItemRequest request, Authentication authentication) {
        return evaluationService.addCurriculum(diaryId, request, authentication);
    }
}
