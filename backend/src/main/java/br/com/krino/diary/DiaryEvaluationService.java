package br.com.krino.diary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;

@Service
public class DiaryEvaluationService {

    private final JdbcTemplate jdbcTemplate;
    private final DiaryAccessService accessService;
    private final DiaryService diaryService;
    private final SecurityAuditService auditService;

    public DiaryEvaluationService(JdbcTemplate jdbcTemplate, DiaryAccessService accessService, DiaryService diaryService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.diaryService = diaryService;
        this.auditService = auditService;
    }

    public List<AssessmentView> assessments(long diaryId, Authentication authentication) {
        accessService.requireRead(diaryId, authentication);
        return jdbcTemplate.query("select id, diary_id, period, title, assessment_date, max_score from diary_assessment where diary_id = ? order by assessment_date, id",
                (rs, rowNum) -> assessmentView(rs.getLong("id"), rs.getLong("diary_id"), rs.getInt("period"), rs.getString("title"), rs.getDate("assessment_date").toLocalDate(), rs.getBigDecimal("max_score")), diaryId);
    }

    @Transactional
    public AssessmentView createAssessment(long diaryId, AssessmentRequest request, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.requireEdit(diaryId, authentication);
        validateAssessment(request, context);
        Long id = jdbcTemplate.queryForObject(
                "insert into diary_assessment (diary_id, period, title, assessment_date, max_score, created_by, updated_by) values (?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, diaryId, request.period(), request.title().trim(), request.assessmentDate(), request.maxScore(), authentication.getName(), authentication.getName());
        auditService.record(authentication.getName(), "DIARY_ASSESSMENT_CREATED", "DIARY", Long.toString(diaryId), request.title().trim());
        return assessment(id);
    }

    @Transactional
    public AssessmentView updateAssessment(long diaryId, long assessmentId, AssessmentRequest request, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.requireEdit(diaryId, authentication);
        ensureAssessmentDiary(assessmentId, diaryId);
        validateAssessment(request, context);
        jdbcTemplate.update("update diary_assessment set period = ?, title = ?, assessment_date = ?, max_score = ?, updated_by = ?, updated_at = current_timestamp where id = ?",
                request.period(), request.title().trim(), request.assessmentDate(), request.maxScore(), authentication.getName(), assessmentId);
        auditService.record(authentication.getName(), "DIARY_ASSESSMENT_UPDATED", "DIARY", Long.toString(diaryId), request.title().trim());
        return assessment(assessmentId);
    }

    @Transactional
    public AssessmentView saveGrades(long diaryId, long assessmentId, List<GradeInput> grades, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.requireEdit(diaryId, authentication);
        ensureAssessmentDiary(assessmentId, diaryId);
        BigDecimal maxScore = jdbcTemplate.queryForObject("select max_score from diary_assessment where id = ?", BigDecimal.class, assessmentId);
        if (grades != null) {
            for (GradeInput grade : grades) {
                ensureEnrollment(context.classId(), grade.enrollmentId());
                if (grade.score() != null && grade.score().signum() < 0) throw new IllegalArgumentException("A nota não pode ser negativa.");
                if (grade.score() != null && maxScore != null && grade.score().compareTo(maxScore) > 0) {
                    throw new IllegalArgumentException("A nota não pode ultrapassar a pontuação máxima da avaliação.");
                }
                jdbcTemplate.update(
                        "insert into diary_assessment_grade (assessment_id, enrollment_id, score, observation) values (?, ?, ?, ?) on conflict (assessment_id, enrollment_id) do update set score = excluded.score, observation = excluded.observation, updated_at = current_timestamp",
                        assessmentId, grade.enrollmentId(), grade.score(), grade.observation());
            }
        }
        auditService.record(authentication.getName(), "DIARY_GRADES_SAVED", "DIARY", Long.toString(diaryId), "Notas da avaliação " + assessmentId + " atualizadas.");
        return assessment(assessmentId);
    }

    public List<PlanningView> planning(long diaryId, Authentication authentication) {
        accessService.requireRead(diaryId, authentication);
        return jdbcTemplate.query("select id, diary_id, period, title, description, updated_by from diary_planning where diary_id = ? order by period",
                (rs, rowNum) -> planningView(rs.getLong("id"), rs.getLong("diary_id"), rs.getInt("period"), rs.getString("title"), rs.getString("description"), rs.getString("updated_by")), diaryId);
    }

    @Transactional
    public PlanningView savePlanning(long diaryId, int period, PlanningRequest request, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.requireEdit(diaryId, authentication);
        if (period < 1 || period > 4) throw new IllegalArgumentException("O período deve estar entre 1 e 4.");
        Long planningId = jdbcTemplate.queryForObject(
                "insert into diary_planning (diary_id, period, title, description, updated_by) values (?, ?, ?, ?, ?) on conflict (diary_id, period) do update set title = excluded.title, description = excluded.description, updated_by = excluded.updated_by, updated_at = current_timestamp returning id",
                Long.class, diaryId, period, request.title().trim(), request.description().trim(), authentication.getName());
        jdbcTemplate.update("delete from diary_planning_curriculum where planning_id = ?", planningId);
        if (request.curriculumItemIds() != null) {
            for (Long itemId : request.curriculumItemIds().stream().distinct().toList()) {
                ensureCurriculumApplicable(context, itemId);
                jdbcTemplate.update("insert into diary_planning_curriculum (planning_id, curriculum_item_id) values (?, ?)", planningId, itemId);
            }
        }
        auditService.record(authentication.getName(), "DIARY_PLANNING_SAVED", "DIARY", Long.toString(diaryId), "Planejamento do período " + period + " atualizado.");
        return planningView(planningId, diaryId, period, request.title().trim(), request.description().trim(), authentication.getName());
    }

    public List<CurriculumItemView> curriculum(long diaryId, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.requireRead(diaryId, authentication);
        return jdbcTemplate.query(
                "select ci.id, ci.source, ci.stage, ci.component_id, cc.name component_name, ci.code, ci.description from curriculum_item ci left join curricular_component cc on cc.id = ci.component_id where ci.active = true and ci.stage = ? and (ci.component_id is null or ci.component_id = ?) order by ci.source, ci.code",
                (rs, rowNum) -> new CurriculumItemView(rs.getLong("id"), rs.getString("source"), rs.getString("stage"), nullableLong(rs, "component_id"), rs.getString("component_name"), rs.getString("code"), rs.getString("description")),
                context.stage(), context.componentId());
    }

    @Transactional
    public CurriculumItemView addCurriculum(long diaryId, CurriculumItemRequest request, Authentication authentication) {
        DiaryAccessService.DiaryContext context = accessService.context(diaryId);
        accessService.requireCurriculumManage(context.schoolId(), authentication);
        Long id = jdbcTemplate.queryForObject(
                "insert into curriculum_item (source, stage, component_id, code, description) values (?, ?, ?, ?, ?) returning id",
                Long.class, request.source().trim(), context.stage(), context.componentId(), request.code().trim(), request.description().trim());
        auditService.record(authentication.getName(), "CURRICULUM_ITEM_CREATED", "CURRICULUM_ITEM", Long.toString(id), request.source().trim() + " / " + request.code().trim());
        return curriculumItem(id);
    }

    private AssessmentView assessment(long assessmentId) {
        List<AssessmentView> rows = jdbcTemplate.query("select id, diary_id, period, title, assessment_date, max_score from diary_assessment where id = ?",
                (rs, rowNum) -> assessmentView(rs.getLong("id"), rs.getLong("diary_id"), rs.getInt("period"), rs.getString("title"), rs.getDate("assessment_date").toLocalDate(), rs.getBigDecimal("max_score")), assessmentId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Avaliação não encontrada.");
        return rows.getFirst();
    }

    private AssessmentView assessmentView(long id, long diaryId, int period, String title, LocalDate date, BigDecimal maxScore) {
        List<GradeView> grades = jdbcTemplate.query(
                "select g.enrollment_id, st.id student_id, st.name student_name, g.score, g.observation from diary_assessment_grade g join student_enrollment e on e.id = g.enrollment_id join student st on st.id = e.student_id where g.assessment_id = ? order by st.name",
                (rs, rowNum) -> new GradeView(rs.getLong("enrollment_id"), rs.getLong("student_id"), rs.getString("student_name"), rs.getBigDecimal("score"), rs.getString("observation")), id);
        return new AssessmentView(id, diaryId, period, title, date, maxScore, grades);
    }

    private PlanningView planningView(long id, long diaryId, int period, String title, String description, String updatedBy) {
        List<CurriculumItemView> items = jdbcTemplate.query(
                "select ci.id, ci.source, ci.stage, ci.component_id, cc.name component_name, ci.code, ci.description from diary_planning_curriculum pc join curriculum_item ci on ci.id = pc.curriculum_item_id left join curricular_component cc on cc.id = ci.component_id where pc.planning_id = ? order by ci.source, ci.code",
                (rs, rowNum) -> new CurriculumItemView(rs.getLong("id"), rs.getString("source"), rs.getString("stage"), nullableLong(rs, "component_id"), rs.getString("component_name"), rs.getString("code"), rs.getString("description")), id);
        return new PlanningView(id, diaryId, period, title, description, updatedBy, items);
    }

    private CurriculumItemView curriculumItem(long id) {
        return jdbcTemplate.queryForObject(
                "select ci.id, ci.source, ci.stage, ci.component_id, cc.name component_name, ci.code, ci.description from curriculum_item ci left join curricular_component cc on cc.id = ci.component_id where ci.id = ?",
                (rs, rowNum) -> new CurriculumItemView(rs.getLong("id"), rs.getString("source"), rs.getString("stage"), nullableLong(rs, "component_id"), rs.getString("component_name"), rs.getString("code"), rs.getString("description")), id);
    }

    private void validateAssessment(AssessmentRequest request, DiaryAccessService.DiaryContext context) {
        diaryService.validateTeachingDate(context, request.assessmentDate());
        if (request.maxScore() != null && request.maxScore().signum() <= 0) throw new IllegalArgumentException("A pontuação máxima deve ser maior que zero.");
    }

    private void ensureAssessmentDiary(long assessmentId, long diaryId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from diary_assessment where id = ? and diary_id = ?", Integer.class, assessmentId, diaryId);
        if (count == null || count == 0) throw new IllegalArgumentException("Avaliação não pertence a este diário.");
    }

    private void ensureEnrollment(long classId, long enrollmentId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from student_enrollment where id = ? and class_id = ?", Integer.class, enrollmentId, classId);
        if (count == null || count == 0) throw new IllegalArgumentException("A nota contém estudante que não pertence a esta turma.");
    }

    private void ensureCurriculumApplicable(DiaryAccessService.DiaryContext context, long itemId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from curriculum_item where id = ? and active = true and stage = ? and (component_id is null or component_id = ?)",
                Integer.class, itemId, context.stage(), context.componentId());
        if (count == null || count == 0) throw new IllegalArgumentException("A referência curricular selecionada não se aplica a este diário.");
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record AssessmentRequest(@NotNull @Min(1) @Max(4) Integer period, @NotBlank(message = "Informe o nome da avaliação.") String title,
                                    @NotNull(message = "Informe a data da avaliação.") LocalDate assessmentDate, @Positive BigDecimal maxScore) {}
    public record GradeInput(@NotNull Long enrollmentId, BigDecimal score, String observation) {}
    public record GradeView(Long enrollmentId, Long studentId, String studentName, BigDecimal score, String observation) {}
    public record AssessmentView(Long id, Long diaryId, Integer period, String title, LocalDate assessmentDate, BigDecimal maxScore, List<GradeView> grades) {}
    public record PlanningRequest(@NotBlank(message = "Informe o título do planejamento.") String title, @NotBlank(message = "Informe o planejamento pedagógico.") String description, List<Long> curriculumItemIds) {}
    public record PlanningView(Long id, Long diaryId, Integer period, String title, String description, String updatedBy, List<CurriculumItemView> curriculumItems) {}
    public record CurriculumItemRequest(@NotBlank(message = "Informe a fonte curricular validada.") String source, @NotBlank(message = "Informe o código da referência curricular.") String code, @NotBlank(message = "Informe a descrição da referência curricular.") String description) {}
    public record CurriculumItemView(Long id, String source, String stage, Long componentId, String componentName, String code, String description) {}
}
