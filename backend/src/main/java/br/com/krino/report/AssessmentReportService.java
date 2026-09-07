package br.com.krino.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Service
public class AssessmentReportService {

    private final JdbcTemplate jdbcTemplate;
    private final ReportAccessService accessService;

    public AssessmentReportService(JdbcTemplate jdbcTemplate, ReportAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public ReportContext context(Integer year, Authentication authentication) {
        List<Long> schoolIds = accessService.readableSchoolIds(authentication);
        if (schoolIds.isEmpty()) return new ReportContext(accessService.hasNetworkRead(authentication), List.of(), List.of());
        String in = placeholders(schoolIds.size());
        List<SchoolOption> schools = jdbcTemplate.query(
                "select id, name from school_unit where active = true and id in (" + in + ") order by name",
                (rs, rowNum) -> new SchoolOption(rs.getLong("id"), rs.getString("name")), schoolIds.toArray());

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "select distinct a.id, a.name, a.stage, a.academic_year, a.grade_stage, cc.name component_name "
                        + "from network_assessment a left join curricular_component cc on cc.id = a.component_id "
                        + "join network_assessment_scope s on s.assessment_id = a.id where s.school_id in (")
                .append(in).append(")");
        params.addAll(schoolIds);
        if (year != null) { sql.append(" and a.academic_year = ?"); params.add(year); }
        sql.append(" order by a.academic_year desc, a.name");
        List<AssessmentOption> assessments = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AssessmentOption(
                rs.getLong("id"), rs.getString("name"), rs.getString("stage"), rs.getInt("academic_year"),
                rs.getString("grade_stage"), rs.getString("component_name")), params.toArray());
        return new ReportContext(accessService.hasNetworkRead(authentication), schools, assessments);
    }

    public DashboardView dashboard(long assessmentId, Long schoolId, Long classId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        Long runId = latestRunId(assessmentId);
        Scope scope = scope(authentication, schoolId, classId, null, "aa");

        List<Object> baseParams = new ArrayList<>();
        baseParams.add(assessmentId);
        baseParams.addAll(scope.parameters());
        Long expected = jdbcTemplate.queryForObject(
                "select count(distinct aa.student_id) from network_assessment_assignment aa where aa.assessment_id = ?" + scope.sql(),
                Long.class, baseParams.toArray());

        long participants = 0;
        long correct = 0;
        long total = 0;
        int skills = 0;
        if (runId != null) {
            List<Object> resultParams = new ArrayList<>(); resultParams.add(runId); resultParams.add(assessmentId); resultParams.addAll(scope.parameters());
            DashboardAggregate aggregate = jdbcTemplate.queryForObject(
                    "select count(distinct aa.student_id) participants, coalesce(sum(r.correct_answers),0) correct_sum, coalesce(sum(r.total_questions),0) total_sum "
                            + "from network_assessment_result r join network_assessment_assignment aa on aa.id = r.assignment_id "
                            + "where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql(),
                    (rs, rowNum) -> new DashboardAggregate(rs.getLong("participants"), rs.getLong("correct_sum"), rs.getLong("total_sum")), resultParams.toArray());
            if (aggregate != null) { participants = aggregate.participants(); correct = aggregate.correctSum(); total = aggregate.totalSum(); }
            List<Object> skillParams = new ArrayList<>(); skillParams.add(runId); skillParams.add(assessmentId); skillParams.addAll(scope.parameters());
            Integer skillCount = jdbcTemplate.queryForObject(
                    "select count(*) from (select rs.descriptor, rs.skill from network_assessment_result_skill rs "
                            + "join network_assessment_result r on r.id = rs.result_id join network_assessment_assignment aa on aa.id = r.assignment_id "
                            + "where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql() + " group by rs.descriptor, rs.skill) x",
                    Integer.class, skillParams.toArray());
            skills = skillCount == null ? 0 : skillCount;
        }

        List<BreakdownRow> schoolBreakdown = participation(assessmentId, "SCHOOL", schoolId, classId, authentication);
        List<SkillRow> skillRows = runId == null ? List.of() : skills(assessmentId, schoolId, classId, null, authentication);
        return new DashboardView(expected == null ? 0 : expected, participants,
                percentage(participants, expected == null ? 0 : expected), percentage(correct, total), skills,
                schoolBreakdown, skillRows.stream().limit(12).toList());
    }

    public List<SchoolSkillRow> schoolSkills(long assessmentId, Long schoolId, Long classId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        Long runId = latestRunId(assessmentId);
        if (runId == null) return List.of();
        Scope scope = scope(authentication, schoolId, classId, null, "aa");
        List<Object> params = new ArrayList<>(); params.add(runId); params.add(assessmentId); params.addAll(scope.parameters());
        List<PerformanceLevel> levels = performanceLevels(assessmentId);
        return jdbcTemplate.query(
                "select aa.school_id, su.name school_name, rs.descriptor, rs.skill, sum(rs.correct_answers) correct_answers, sum(rs.total_questions) total_questions "
                        + "from network_assessment_result_skill rs join network_assessment_result r on r.id = rs.result_id "
                        + "join network_assessment_assignment aa on aa.id = r.assignment_id join school_unit su on su.id = aa.school_id "
                        + "where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql()
                        + " group by aa.school_id, su.name, rs.descriptor, rs.skill order by su.name, rs.descriptor, rs.skill",
                (rs, rowNum) -> {
                    long correct = rs.getLong("correct_answers"); long total = rs.getLong("total_questions"); BigDecimal value = percentage(correct, total);
                    return new SchoolSkillRow(rs.getLong("school_id"), rs.getString("school_name"), rs.getString("descriptor"), rs.getString("skill"),
                            correct, total, value, classify(value, levels));
                }, params.toArray());
    }

    public List<AlternativeRow> alternatives(long assessmentId, Long schoolId, Long classId, Long studentId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        Long runId = latestRunId(assessmentId);
        if (runId == null) return List.of();
        Scope scope = scope(authentication, schoolId, classId, studentId, "aa");
        List<Object> params = new ArrayList<>(); params.add(runId); params.add(assessmentId); params.addAll(scope.parameters());
        List<AlternativeCount> counts = jdbcTemplate.query(
                "select q.sequence_number, q.descriptor, q.skill, q.correct_option, upper(coalesce(ans.selected_option, 'SEM_RESPOSTA')) selected_option, count(*) responses "
                        + "from network_assessment_result r join network_assessment_assignment aa on aa.id = r.assignment_id "
                        + "join network_assessment_answer_sheet sh on sh.id = r.answer_sheet_id "
                        + "join network_assessment_answer ans on ans.answer_sheet_id = sh.id "
                        + "join network_assessment_question q on q.id = ans.question_id "
                        + "where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql()
                        + " group by q.sequence_number, q.descriptor, q.skill, q.correct_option, upper(coalesce(ans.selected_option, 'SEM_RESPOSTA')) "
                        + "order by q.sequence_number, selected_option",
                (rs, rowNum) -> new AlternativeCount(rs.getInt("sequence_number"), rs.getString("descriptor"), rs.getString("skill"),
                        rs.getString("correct_option"), rs.getString("selected_option"), rs.getLong("responses")), params.toArray());
        Map<Integer, Long> totals = new LinkedHashMap<>();
        counts.forEach(row -> totals.merge(row.sequenceNumber(), row.responses(), Long::sum));
        return counts.stream().map(row -> new AlternativeRow(row.sequenceNumber(), row.descriptor(), row.skill(), row.correctOption(), row.selectedOption(),
                row.responses(), totals.getOrDefault(row.sequenceNumber(), 0L), percentage(row.responses(), totals.getOrDefault(row.sequenceNumber(), 0L)))).toList();
    }

    public List<QuestionRow> questions(long assessmentId, Long schoolId, Long classId, Long studentId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        Long runId = latestRunId(assessmentId);
        if (runId == null) return List.of();
        Scope scope = scope(authentication, schoolId, classId, studentId, "aa");
        List<Object> params = new ArrayList<>(); params.add(runId); params.add(assessmentId); params.addAll(scope.parameters());
        List<QuestionRow> rows = jdbcTemplate.query(
                "select q.sequence_number, q.descriptor, q.skill, count(*) responses, "
                        + "sum(case when upper(coalesce(ans.selected_option,'')) = upper(q.correct_option) then 1 else 0 end) correct_answers "
                        + "from network_assessment_result r join network_assessment_assignment aa on aa.id = r.assignment_id "
                        + "join network_assessment_answer_sheet sh on sh.id = r.answer_sheet_id join network_assessment_answer ans on ans.answer_sheet_id = sh.id "
                        + "join network_assessment_question q on q.id = ans.question_id where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql()
                        + " group by q.sequence_number, q.descriptor, q.skill order by q.sequence_number",
                (rs, rowNum) -> {
                    long responses = rs.getLong("responses"); long correct = rs.getLong("correct_answers");
                    return new QuestionRow(rs.getInt("sequence_number"), rs.getString("descriptor"), rs.getString("skill"), correct, responses,
                            percentage(correct, responses), "Intermediária");
                }, params.toArray());
        if (rows.isEmpty()) return rows;
        BigDecimal min = rows.stream().map(QuestionRow::correctPercent).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal max = rows.stream().map(QuestionRow::correctPercent).filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return rows.stream().map(row -> new QuestionRow(row.sequenceNumber(), row.descriptor(), row.skill(), row.correctAnswers(), row.responses(), row.correctPercent(),
                row.correctPercent() == null ? "Sem base" : row.correctPercent().compareTo(min) == 0 && row.correctPercent().compareTo(max) != 0 ? "Maior dificuldade relativa" : row.correctPercent().compareTo(max) == 0 && row.correctPercent().compareTo(min) != 0 ? "Menor dificuldade relativa" : "Intermediária")).toList();
    }

    public List<SkillRow> skills(long assessmentId, Long schoolId, Long classId, Long studentId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        Long runId = latestRunId(assessmentId);
        if (runId == null) return List.of();
        Scope scope = scope(authentication, schoolId, classId, studentId, "aa");
        List<Object> params = new ArrayList<>(); params.add(runId); params.add(assessmentId); params.addAll(scope.parameters());
        List<PerformanceLevel> levels = performanceLevels(assessmentId);
        return jdbcTemplate.query(
                "select rs.descriptor, rs.skill, sum(rs.correct_answers) correct_answers, sum(rs.total_questions) total_questions "
                        + "from network_assessment_result_skill rs join network_assessment_result r on r.id = rs.result_id "
                        + "join network_assessment_assignment aa on aa.id = r.assignment_id where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql()
                        + " group by rs.descriptor, rs.skill order by rs.descriptor, rs.skill",
                (rs, rowNum) -> {
                    long correct = rs.getLong("correct_answers"); long total = rs.getLong("total_questions"); BigDecimal value = percentage(correct, total);
                    return new SkillRow(rs.getString("descriptor"), rs.getString("skill"), correct, total, value, classify(value, levels));
                }, params.toArray());
    }

    public List<ComponentRow> components(long assessmentId, Long schoolId, Long classId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        Long runId = latestRunId(assessmentId);
        if (runId == null) return List.of();
        Scope scope = scope(authentication, schoolId, classId, null, "aa");
        List<Object> params = new ArrayList<>(); params.add(runId); params.add(assessmentId); params.addAll(scope.parameters());
        return jdbcTemplate.query(
                "select coalesce(cc.name, 'Multidisciplinar') component_name, count(distinct aa.student_id) students, sum(r.correct_answers) correct_answers, sum(r.total_questions) total_questions "
                        + "from network_assessment_result r join network_assessment_assignment aa on aa.id = r.assignment_id "
                        + "join network_assessment a on a.id = r.assessment_id left join curricular_component cc on cc.id = a.component_id "
                        + "where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql() + " group by coalesce(cc.name, 'Multidisciplinar') order by component_name",
                (rs, rowNum) -> { long correct = rs.getLong("correct_answers"); long total = rs.getLong("total_questions");
                    return new ComponentRow(rs.getString("component_name"), rs.getLong("students"), correct, total, percentage(correct, total)); }, params.toArray());
    }

    public List<StudentAnswerRow> studentAnswers(long assessmentId, long studentId, Long schoolId, Long classId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        Long runId = latestRunId(assessmentId);
        if (runId == null) return List.of();
        Scope scope = scope(authentication, schoolId, classId, studentId, "aa");
        List<Object> params = new ArrayList<>(); params.add(runId); params.add(assessmentId); params.addAll(scope.parameters());
        return jdbcTemplate.query(
                "select q.sequence_number, q.descriptor, q.skill, ans.selected_option, q.correct_option, "
                        + "case when upper(coalesce(ans.selected_option,'')) = upper(q.correct_option) then true else false end correct "
                        + "from network_assessment_result r join network_assessment_assignment aa on aa.id = r.assignment_id "
                        + "join network_assessment_answer_sheet sh on sh.id = r.answer_sheet_id join network_assessment_answer ans on ans.answer_sheet_id = sh.id "
                        + "join network_assessment_question q on q.id = ans.question_id where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql() + " order by q.sequence_number",
                (rs, rowNum) -> new StudentAnswerRow(rs.getInt("sequence_number"), rs.getString("descriptor"), rs.getString("skill"),
                        rs.getString("selected_option"), rs.getString("correct_option"), rs.getBoolean("correct")), params.toArray());
    }

    public InterventionProfile intervention(long assessmentId, long studentId, Long schoolId, Long classId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        List<StudentIdentity> identities = jdbcTemplate.query(
                "select distinct s.id, s.registration, s.name from network_assessment_assignment aa join student s on s.id = aa.student_id where aa.assessment_id = ? and s.id = ?",
                (rs, rowNum) -> new StudentIdentity(rs.getLong("id"), rs.getString("registration"), rs.getString("name")), assessmentId, studentId);
        if (identities.isEmpty()) throw new IllegalArgumentException("Estudante não participa desta avaliação.");
        List<SkillRow> rows = skills(assessmentId, schoolId, classId, studentId, authentication);
        List<SkillRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(SkillRow::correctPercent, Comparator.nullsLast(Comparator.naturalOrder())));
        List<SkillRow> attention = ordered.stream().limit(Math.min(3, ordered.size())).toList();
        Collections.reverse(ordered);
        List<SkillRow> strengths = ordered.stream().limit(Math.min(3, ordered.size())).toList();
        Long runId = latestRunId(assessmentId);
        BigDecimal overall = null;
        if (runId != null) {
            Scope scope = scope(authentication, schoolId, classId, studentId, "aa");
            List<Object> params = new ArrayList<>(); params.add(runId); params.add(assessmentId); params.addAll(scope.parameters());
            Aggregate aggregate = jdbcTemplate.queryForObject(
                    "select coalesce(sum(r.correct_answers),0) correct_sum, coalesce(sum(r.total_questions),0) total_sum from network_assessment_result r "
                            + "join network_assessment_assignment aa on aa.id = r.assignment_id where r.processing_run_id = ? and r.assessment_id = ?" + scope.sql(),
                    (rs, rowNum) -> new Aggregate(rs.getLong("correct_sum"), rs.getLong("total_sum")), params.toArray());
            if (aggregate != null) overall = percentage(aggregate.correct(), aggregate.total());
        }
        return new InterventionProfile(identities.getFirst().studentId(), identities.getFirst().registration(), identities.getFirst().name(), overall,
                classify(overall, performanceLevels(assessmentId)), strengths, attention);
    }

    public List<BreakdownRow> participation(long assessmentId, String level, Long schoolId, Long classId, Authentication authentication) {
        requireAssessmentVisible(assessmentId, schoolId, authentication);
        String normalized = level == null ? "SCHOOL" : level.trim().toUpperCase();
        if (!List.of("NETWORK", "SCHOOL", "CLASS").contains(normalized)) throw new IllegalArgumentException("Nível de participação inválido.");
        Long runId = latestRunId(assessmentId);
        Scope scope = scope(authentication, schoolId, classId, null, "aa");
        String key;
        String label;
        String group;
        if (normalized.equals("NETWORK")) { key = "0"; label = "'Rede municipal'"; group = ""; }
        else if (normalized.equals("SCHOOL")) { key = "aa.school_id"; label = "su.name"; group = " group by aa.school_id, su.name"; }
        else { key = "aa.class_id"; label = "sc.name"; group = " group by aa.class_id, sc.name"; }
        List<Object> params = new ArrayList<>(); params.add(assessmentId); if (runId != null) params.add(runId); params.addAll(scope.parameters());
        String runJoin = runId == null ? "left join network_assessment_result r on 1=0 " : "left join network_assessment_result r on r.assignment_id = aa.id and r.processing_run_id = ? ";
        return jdbcTemplate.query(
                "select " + key + " key_id, " + label + " label, count(distinct aa.student_id) expected_students, count(distinct case when r.id is not null then aa.student_id end) participants "
                        + "from network_assessment_assignment aa join school_unit su on su.id = aa.school_id join school_class sc on sc.id = aa.class_id "
                        + runJoin + "where aa.assessment_id = ?" + scope.sql() + group + " order by label",
                (rs, rowNum) -> { long expected = rs.getLong("expected_students"); long participants = rs.getLong("participants");
                    return new BreakdownRow(rs.getLong("key_id"), rs.getString("label"), participants, expected, percentage(participants, expected)); },
                reorderParticipationParams(runId, assessmentId, scope.parameters()));
    }

    private Object[] reorderParticipationParams(Long runId, long assessmentId, List<Object> scopeParams) {
        List<Object> params = new ArrayList<>();
        if (runId != null) params.add(runId);
        params.add(assessmentId);
        params.addAll(scopeParams);
        return params.toArray();
    }

    @Transactional
    public List<PerformanceLevel> replacePerformanceLevels(long assessmentId, @NotEmpty List<@Valid PerformanceLevelRequest> requests, Authentication authentication) {
        if (!authentication.isAuthenticated()) throw new AccessDeniedException("Autenticação obrigatória.");
        if (!new br.com.krino.assessment.AssessmentAccessServiceProxy().unsupported()) { /* nunca executado; mantém este serviço sem dependência circular */ }
        validateLevels(requests);
        Integer assessment = jdbcTemplate.queryForObject("select count(*) from network_assessment where id = ?", Integer.class, assessmentId);
        if (assessment == null || assessment == 0) throw new IllegalArgumentException("Avaliação em Rede não encontrada.");
        jdbcTemplate.update("delete from network_assessment_performance_level where assessment_id = ?", assessmentId);
        int order = 1;
        for (PerformanceLevelRequest request : requests) {
            jdbcTemplate.update(
                    "insert into network_assessment_performance_level (assessment_id, label, minimum_percent, maximum_percent, display_order, created_by) values (?, ?, ?, ?, ?, ?)",
                    assessmentId, request.label().trim(), request.minimumPercent(), request.maximumPercent(), order++, authentication.getName());
        }
        return performanceLevels(assessmentId);
    }

    public List<PerformanceLevel> performanceLevels(long assessmentId) {
        return jdbcTemplate.query(
                "select id, label, minimum_percent, maximum_percent, display_order from network_assessment_performance_level where assessment_id = ? order by display_order",
                (rs, rowNum) -> new PerformanceLevel(rs.getLong("id"), rs.getString("label"), rs.getBigDecimal("minimum_percent"), rs.getBigDecimal("maximum_percent"), rs.getInt("display_order")), assessmentId);
    }

    private void validateLevels(List<PerformanceLevelRequest> requests) {
        List<PerformanceLevelRequest> ordered = new ArrayList<>(requests);
        ordered.sort(Comparator.comparing(PerformanceLevelRequest::minimumPercent));
        BigDecimal previousMaximum = null;
        for (PerformanceLevelRequest request : ordered) {
            if (request.minimumPercent().compareTo(request.maximumPercent()) > 0) throw new IllegalArgumentException("A faixa mínima não pode superar a faixa máxima.");
            if (previousMaximum != null && request.minimumPercent().compareTo(previousMaximum) <= 0) throw new IllegalArgumentException("As faixas de desempenho não podem se sobrepor.");
            previousMaximum = request.maximumPercent();
        }
    }

    private void requireAssessmentVisible(long assessmentId, Long schoolId, Authentication authentication) {
        Integer exists = jdbcTemplate.queryForObject("select count(*) from network_assessment where id = ?", Integer.class, assessmentId);
        if (exists == null || exists == 0) throw new IllegalArgumentException("Avaliação em Rede não encontrada.");
        if (schoolId != null) { accessService.requireRead(authentication, schoolId); return; }
        if (accessService.hasNetworkRead(authentication)) return;
        List<Long> ids = accessService.readableSchoolIds(authentication);
        if (ids.isEmpty()) throw new AccessDeniedException("Sua conta não possui acesso aos relatórios desta avaliação.");
        List<Object> params = new ArrayList<>(); params.add(assessmentId); params.addAll(ids);
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from network_assessment_scope where assessment_id = ? and school_id in (" + placeholders(ids.size()) + ")",
                Integer.class, params.toArray());
        if (count == null || count == 0) throw new AccessDeniedException("Sua conta não possui acesso aos relatórios desta avaliação.");
    }

    private Scope scope(Authentication authentication, Long schoolId, Long classId, Long studentId, String alias) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        if (schoolId != null) {
            accessService.requireRead(authentication, schoolId);
            sql.append(" and ").append(alias).append(".school_id = ?"); params.add(schoolId);
        } else if (!accessService.hasNetworkRead(authentication)) {
            List<Long> ids = accessService.readableSchoolIds(authentication);
            if (ids.isEmpty()) throw new AccessDeniedException("Sua conta não possui unidade escolar autorizada para relatórios.");
            sql.append(" and ").append(alias).append(".school_id in (").append(placeholders(ids.size())).append(")"); params.addAll(ids);
        }
        if (classId != null) { sql.append(" and ").append(alias).append(".class_id = ?"); params.add(classId); }
        if (studentId != null) { sql.append(" and ").append(alias).append(".student_id = ?"); params.add(studentId); }
        return new Scope(sql.toString(), params);
    }

    private Long latestRunId(long assessmentId) {
        List<Long> ids = jdbcTemplate.query(
                "select id from network_assessment_processing_run where assessment_id = ? and status = 'COMPLETED' order by run_number desc limit 1",
                (rs, rowNum) -> rs.getLong("id"), assessmentId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private String classify(BigDecimal value, List<PerformanceLevel> levels) {
        if (value == null) return "Sem base";
        return levels.stream().filter(level -> value.compareTo(level.minimumPercent()) >= 0 && value.compareTo(level.maximumPercent()) <= 0)
                .map(PerformanceLevel::label).findFirst().orElse("Não parametrizada");
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) return null;
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }

    public record ReportContext(boolean networkView, List<SchoolOption> schools, List<AssessmentOption> assessments) {}
    public record SchoolOption(long id, String name) {}
    public record AssessmentOption(long id, String name, String stage, int academicYear, String gradeStage, String componentName) {}
    public record DashboardView(long expectedStudents, long participants, BigDecimal participationPercent, BigDecimal achievementPercent,
            int skills, List<BreakdownRow> participationBySchool, List<SkillRow> skillHighlights) {}
    public record BreakdownRow(long keyId, String label, long participants, long expectedStudents, BigDecimal percentage) {}
    public record SchoolSkillRow(long schoolId, String schoolName, String descriptor, String skill, long correctAnswers, long totalQuestions,
            BigDecimal correctPercent, String performanceLevel) {}
    public record AlternativeRow(int sequenceNumber, String descriptor, String skill, String correctOption, String selectedOption,
            long responses, long baseResponses, BigDecimal responsePercent) {}
    public record QuestionRow(int sequenceNumber, String descriptor, String skill, long correctAnswers, long responses,
            BigDecimal correctPercent, String relativeComplexity) {}
    public record SkillRow(String descriptor, String skill, long correctAnswers, long totalQuestions, BigDecimal correctPercent, String performanceLevel) {}
    public record ComponentRow(String componentName, long students, long correctAnswers, long totalQuestions, BigDecimal correctPercent) {}
    public record StudentAnswerRow(int sequenceNumber, String descriptor, String skill, String selectedOption, String correctOption, boolean correct) {}
    public record InterventionProfile(long studentId, String registration, String studentName, BigDecimal correctPercent, String performanceLevel,
            List<SkillRow> strengths, List<SkillRow> attentionPriorities) {}
    public record PerformanceLevel(long id, String label, BigDecimal minimumPercent, BigDecimal maximumPercent, int displayOrder) {}
    public record PerformanceLevelRequest(@NotBlank String label,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minimumPercent,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maximumPercent) {}

    private record DashboardAggregate(long participants, long correctSum, long totalSum) {}
    private record Aggregate(long correct, long total) {}
    private record AlternativeCount(int sequenceNumber, String descriptor, String skill, String correctOption, String selectedOption, long responses) {}
    private record StudentIdentity(long studentId, String registration, String name) {}
    private record Scope(String sql, List<Object> parameters) {}
}
