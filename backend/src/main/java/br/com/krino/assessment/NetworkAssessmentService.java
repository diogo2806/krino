package br.com.krino.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.krino.assessment.AssessmentResultCalculator.AnswerEvaluation;
import br.com.krino.audit.SecurityAuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Service
public class NetworkAssessmentService {

    private static final Set<String> STAGES = Set.of("DIAGNOSTIC", "MONITORING", "FINAL");
    private static final Set<String> ATTENDANCE_STATUSES = Set.of("PENDING", "PRESENT", "ABSENT", "MAKEUP");
    private static final Set<String> SOURCE_TYPES = Set.of("MANUAL", "IMPORT", "ONLINE");
    private static final Set<String> RESULT_LEVELS = Set.of("NETWORK", "SCHOOL", "CLASS", "STUDENT");

    private final JdbcTemplate jdbcTemplate;
    private final AssessmentAccessService accessService;
    private final AssessmentResultCalculator resultCalculator;
    private final SecurityAuditService auditService;
    private final ObjectMapper objectMapper;

    public NetworkAssessmentService(JdbcTemplate jdbcTemplate, AssessmentAccessService accessService,
            AssessmentResultCalculator resultCalculator, SecurityAuditService auditService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.resultCalculator = resultCalculator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public CatalogView catalog(Authentication authentication) {
        List<Long> schoolIds = accessService.readableSchoolIds(authentication);
        if (schoolIds.isEmpty()) return new CatalogView(List.of(), List.of(), components());
        String in = placeholders(schoolIds.size());
        List<SchoolOption> schools = jdbcTemplate.query(
                "select id, name from school_unit where active = true and id in (" + in + ") order by name",
                (rs, rowNum) -> new SchoolOption(rs.getLong("id"), rs.getString("name")), schoolIds.toArray());
        List<ClassOption> classes = jdbcTemplate.query(
                "select id, school_id, name, stage, academic_year from school_class where active = true and school_id in (" + in + ") order by academic_year desc, stage, name",
                (rs, rowNum) -> new ClassOption(rs.getLong("id"), rs.getLong("school_id"), rs.getString("name"), rs.getString("stage"), rs.getInt("academic_year")),
                schoolIds.toArray());
        return new CatalogView(schools, classes, components());
    }

    public List<AssessmentView> list(Integer academicYear, String stage, Long schoolId, Authentication authentication) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(assessmentSelect()).append(" where 1=1");
        if (academicYear != null) {
            sql.append(" and a.academic_year = ?");
            parameters.add(academicYear);
        }
        if (stage != null && !stage.isBlank()) {
            String normalized = normalizeStage(stage);
            sql.append(" and a.stage = ?");
            parameters.add(normalized);
        }
        if (schoolId != null) {
            accessService.requireRead(authentication, schoolId);
            sql.append(" and exists (select 1 from network_assessment_scope sx where sx.assessment_id = a.id and sx.school_id = ?)");
            parameters.add(schoolId);
        } else if (!accessService.hasNetworkRead(authentication)) {
            List<Long> schoolIds = accessService.readableSchoolIds(authentication);
            if (schoolIds.isEmpty()) return List.of();
            sql.append(" and exists (select 1 from network_assessment_scope sx where sx.assessment_id = a.id and sx.school_id in (")
                    .append(placeholders(schoolIds.size())).append("))");
            parameters.addAll(schoolIds);
        }
        sql.append(" order by a.academic_year desc, a.id desc");
        return jdbcTemplate.query(sql.toString(), assessmentMapper(), parameters.toArray());
    }

    public AssessmentView get(long assessmentId, Authentication authentication) {
        AssessmentView view = loadAssessment(assessmentId);
        requireAssessmentRead(assessmentId, authentication);
        return view;
    }

    @Transactional
    public AssessmentView create(@Valid AssessmentRequest request, Authentication authentication) {
        accessService.requireNetworkWrite(authentication);
        String stage = normalizeStage(request.stage());
        validateAcademicYear(request.academicYear());
        if (request.componentId() != null) requireComponent(request.componentId());
        Long id = jdbcTemplate.queryForObject(
                "insert into network_assessment (name, stage, academic_year, grade_stage, component_id, application_manual, instructions, created_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, request.name().trim(), stage, request.academicYear(), request.gradeStage().trim(), request.componentId(),
                blankToNull(request.applicationManual()), blankToNull(request.instructions()), authentication.getName());
        auditService.record(authentication.getName(), "ASSESSMENT_CREATE", "NETWORK_ASSESSMENT", String.valueOf(id),
                "Etapa=" + stage + ", ano=" + request.academicYear() + ", série=" + request.gradeStage().trim());
        return loadAssessment(id);
    }

    @Transactional
    public List<QuestionView> replaceQuestions(long assessmentId, @NotEmpty List<@Valid QuestionRequest> questions, Authentication authentication) {
        accessService.requireNetworkWrite(authentication);
        loadAssessment(assessmentId);
        Integer sheets = jdbcTemplate.queryForObject("select count(*) from network_assessment_answer_sheet where assessment_id = ?", Integer.class, assessmentId);
        if (sheets != null && sheets > 0) {
            throw new IllegalArgumentException("Não é possível alterar as questões depois que gabaritos foram recebidos.");
        }
        Set<Integer> sequences = new HashSet<>();
        for (QuestionRequest question : questions) {
            if (!sequences.add(question.sequenceNumber())) throw new IllegalArgumentException("A numeração das questões não pode se repetir.");
        }
        jdbcTemplate.update("delete from network_assessment_question where assessment_id = ?", assessmentId);
        for (QuestionRequest question : questions) {
            jdbcTemplate.update(
                    "insert into network_assessment_question (assessment_id, sequence_number, descriptor, skill, correct_option) values (?, ?, ?, ?, ?)",
                    assessmentId, question.sequenceNumber(), question.descriptor().trim(), question.skill().trim(), question.correctOption().trim().toUpperCase(Locale.ROOT));
        }
        jdbcTemplate.update("update network_assessment set updated_at = current_timestamp where id = ?", assessmentId);
        auditService.record(authentication.getName(), "ASSESSMENT_QUESTIONS_REPLACE", "NETWORK_ASSESSMENT", String.valueOf(assessmentId),
                "Questões configuradas=" + questions.size());
        return questions(assessmentId, authentication);
    }

    public List<QuestionView> questions(long assessmentId, Authentication authentication) {
        requireAssessmentRead(assessmentId, authentication);
        return jdbcTemplate.query(
                "select id, sequence_number, descriptor, skill, correct_option from network_assessment_question where assessment_id = ? and active = true order by sequence_number",
                (rs, rowNum) -> new QuestionView(rs.getLong("id"), rs.getInt("sequence_number"), rs.getString("descriptor"), rs.getString("skill"), rs.getString("correct_option")),
                assessmentId);
    }

    @Transactional
    public OrganizationSummary organize(long assessmentId, @Valid OrganizeRequest request, Authentication authentication) {
        accessService.requireNetworkWrite(authentication);
        AssessmentView assessment = loadAssessment(assessmentId);
        Integer sheets = jdbcTemplate.queryForObject("select count(*) from network_assessment_answer_sheet where assessment_id = ?", Integer.class, assessmentId);
        if (sheets != null && sheets > 0) throw new IllegalArgumentException("Não é possível reorganizar estudantes depois que gabaritos foram recebidos.");

        jdbcTemplate.update("delete from network_assessment_assignment where assessment_id = ?", assessmentId);
        jdbcTemplate.update("delete from network_assessment_scope where assessment_id = ?", assessmentId);

        int classes = 0;
        int students = 0;
        Set<Long> schools = new HashSet<>();
        for (Long classId : new LinkedHashMap<Long, Boolean>() {{ request.classIds().forEach(id -> put(id, Boolean.TRUE)); }}.keySet()) {
            ClassScope scope = classScope(classId);
            if (scope.academicYear() != assessment.academicYear()) {
                throw new IllegalArgumentException("A turma " + scope.className() + " não pertence ao ano letivo da avaliação.");
            }
            if (!scope.stage().trim().equalsIgnoreCase(assessment.gradeStage().trim())) {
                throw new IllegalArgumentException("A turma " + scope.className() + " não pertence à etapa/ano-série configurada na avaliação.");
            }
            jdbcTemplate.update("insert into network_assessment_scope (assessment_id, school_id, class_id) values (?, ?, ?)", assessmentId, scope.schoolId(), classId);
            List<EnrollmentRow> enrollments = jdbcTemplate.query(
                    "select e.id enrollment_id, e.student_id from student_enrollment e where e.class_id = ? and e.academic_year = ? and e.status = 'ACTIVE' order by e.student_id",
                    (rs, rowNum) -> new EnrollmentRow(rs.getLong("enrollment_id"), rs.getLong("student_id")), classId, assessment.academicYear());
            String packageCode = "AV%06d-T%06d".formatted(assessmentId, classId);
            for (EnrollmentRow enrollment : enrollments) {
                String labelCode = "AV%06d-AL%08d".formatted(assessmentId, enrollment.enrollmentId());
                String onlineCode = UUID.randomUUID().toString().replace("-", "");
                jdbcTemplate.update(
                        "insert into network_assessment_assignment (assessment_id, enrollment_id, student_id, school_id, class_id, label_code, package_code, online_access_code) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        assessmentId, enrollment.enrollmentId(), enrollment.studentId(), scope.schoolId(), classId, labelCode, packageCode, onlineCode);
                students++;
            }
            classes++;
            schools.add(scope.schoolId());
        }
        jdbcTemplate.update("update network_assessment set status = 'READY', updated_at = current_timestamp where id = ?", assessmentId);
        auditService.record(authentication.getName(), "ASSESSMENT_ORGANIZE", "NETWORK_ASSESSMENT", String.valueOf(assessmentId),
                "Escolas=" + schools.size() + ", turmas=" + classes + ", estudantes=" + students);
        return new OrganizationSummary(schools.size(), classes, students);
    }

    public List<AssignmentView> assignments(long assessmentId, Long schoolId, Long classId, Authentication authentication) {
        requireAssessmentRead(assessmentId, authentication);
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "select aa.id, aa.student_id, s.registration, s.name student_name, aa.school_id, su.name school_name, aa.class_id, sc.name class_name, "
                        + "aa.attendance_status, aa.label_code, aa.package_code "
                        + "from network_assessment_assignment aa join student s on s.id = aa.student_id join school_unit su on su.id = aa.school_id join school_class sc on sc.id = aa.class_id "
                        + "where aa.assessment_id = ?");
        parameters.add(assessmentId);
        if (schoolId != null) {
            accessService.requireRead(authentication, schoolId);
            sql.append(" and aa.school_id = ?");
            parameters.add(schoolId);
        } else if (!accessService.hasNetworkRead(authentication)) {
            List<Long> ids = accessService.readableSchoolIds(authentication);
            if (ids.isEmpty()) return List.of();
            sql.append(" and aa.school_id in (").append(placeholders(ids.size())).append(")");
            parameters.addAll(ids);
        }
        if (classId != null) {
            sql.append(" and aa.class_id = ?");
            parameters.add(classId);
        }
        sql.append(" order by su.name, sc.name, s.name");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AssignmentView(
                rs.getLong("id"), rs.getLong("student_id"), rs.getString("registration"), rs.getString("student_name"),
                rs.getLong("school_id"), rs.getString("school_name"), rs.getLong("class_id"), rs.getString("class_name"),
                rs.getString("attendance_status"), rs.getString("label_code"), rs.getString("package_code")), parameters.toArray());
    }

    @Transactional
    public AssignmentView updateAttendance(long assessmentId, long assignmentId, @Valid AttendanceRequest request, Authentication authentication) {
        accessService.requireNetworkWrite(authentication);
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!ATTENDANCE_STATUSES.contains(status)) throw new IllegalArgumentException("Situação de presença inválida.");
        int updated = jdbcTemplate.update("update network_assessment_assignment set attendance_status = ? where id = ? and assessment_id = ?", status, assignmentId, assessmentId);
        if (updated == 0) throw new IllegalArgumentException("Estudante organizado não encontrado nesta avaliação.");
        auditService.record(authentication.getName(), "ASSESSMENT_ATTENDANCE_UPDATE", "NETWORK_ASSESSMENT_ASSIGNMENT", String.valueOf(assignmentId), "Situação=" + status);
        return jdbcTemplate.query(
                "select aa.id, aa.student_id, s.registration, s.name student_name, aa.school_id, su.name school_name, aa.class_id, sc.name class_name, aa.attendance_status, aa.label_code, aa.package_code "
                        + "from network_assessment_assignment aa join student s on s.id = aa.student_id join school_unit su on su.id = aa.school_id join school_class sc on sc.id = aa.class_id where aa.id = ?",
                (rs, rowNum) -> new AssignmentView(rs.getLong("id"), rs.getLong("student_id"), rs.getString("registration"), rs.getString("student_name"), rs.getLong("school_id"), rs.getString("school_name"), rs.getLong("class_id"), rs.getString("class_name"), rs.getString("attendance_status"), rs.getString("label_code"), rs.getString("package_code")),
                assignmentId).getFirst();
    }

    public ArtifactView artifact(long assessmentId, String type, Long schoolId, Long classId, Authentication authentication) {
        accessService.requireNetworkWrite(authentication);
        AssessmentView assessment = loadAssessment(assessmentId);
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        List<String> lines = new ArrayList<>();
        String title;
        switch (normalized) {
            case "ATTENDANCE_LIST" -> {
                title = "Lista de presença nominal";
                List<AssignmentView> rows = assignmentsForArtifact(assessmentId, schoolId, classId);
                rows.forEach(row -> lines.add(row.registration() + " | " + row.studentName() + " | " + row.schoolName() + " | " + row.className() + " | " + row.attendanceStatus()));
            }
            case "LABELS" -> {
                title = "Etiquetas e identificação de pacotes";
                List<AssignmentView> rows = assignmentsForArtifact(assessmentId, schoolId, classId);
                rows.forEach(row -> lines.add(row.labelCode() + " | " + row.packageCode() + " | " + row.studentName() + " | " + row.className()));
            }
            case "MAKEUP_ACCESS" -> {
                title = "Acesso para retardatários e segunda chamada";
                StringBuilder sql = new StringBuilder("select s.registration, s.name, aa.online_access_code from network_assessment_assignment aa join student s on s.id = aa.student_id where aa.assessment_id = ?");
                List<Object> params = new ArrayList<>(); params.add(assessmentId);
                if (schoolId != null) { sql.append(" and aa.school_id = ?"); params.add(schoolId); }
                if (classId != null) { sql.append(" and aa.class_id = ?"); params.add(classId); }
                sql.append(" order by s.name");
                lines.addAll(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> rs.getString("registration") + " | " + rs.getString("name") + " | " + rs.getString("online_access_code"), params.toArray()));
            }
            case "APPLICATOR_MANUAL" -> {
                title = "Manual do Aplicador";
                lines.add(assessment.applicationManual() == null || assessment.applicationManual().isBlank()
                        ? "Manual do Aplicador ainda não informado para esta avaliação."
                        : assessment.applicationManual());
                if (assessment.instructions() != null && !assessment.instructions().isBlank()) lines.add("Orientações adicionais: " + assessment.instructions());
            }
            case "INCIDENT_MINUTES" -> {
                title = "Ata de Ocorrências";
                lines.add("Avaliação: " + assessment.name());
                lines.add("Etapa: " + assessment.stage());
                lines.add("Ano/série: " + assessment.gradeStage());
                lines.add("Data/hora: ____________________");
                lines.add("Unidade escolar: ____________________");
                lines.add("Turma: ____________________");
                lines.add("Ocorrência: ______________________________________________________________");
                lines.add("Responsável pelo registro: ____________________");
            }
            default -> throw new IllegalArgumentException("Artefato inválido. Use ATTENDANCE_LIST, LABELS, MAKEUP_ACCESS, APPLICATOR_MANUAL ou INCIDENT_MINUTES.");
        }
        auditService.record(authentication.getName(), "ASSESSMENT_ARTIFACT_GENERATE", "NETWORK_ASSESSMENT", String.valueOf(assessmentId), "Artefato=" + normalized);
        return new ArtifactView(normalized, title, OffsetDateTime.now(), lines.size(), lines);
    }

    @Transactional
    public OccurrenceView recordOccurrence(long assessmentId, @Valid OccurrenceRequest request, Authentication authentication) {
        accessService.requireNetworkWrite(authentication);
        loadAssessment(assessmentId);
        if (request.classId() != null) {
            Integer count = jdbcTemplate.queryForObject("select count(*) from network_assessment_scope where assessment_id = ? and class_id = ?", Integer.class, assessmentId, request.classId());
            if (count == null || count == 0) throw new IllegalArgumentException("A turma informada não faz parte desta avaliação.");
        }
        Long id = jdbcTemplate.queryForObject(
                "insert into network_assessment_occurrence (assessment_id, school_id, class_id, description, created_by) values (?, ?, ?, ?, ?) returning id",
                Long.class, assessmentId, request.schoolId(), request.classId(), request.description().trim(), authentication.getName());
        auditService.record(authentication.getName(), "ASSESSMENT_OCCURRENCE_CREATE", "NETWORK_ASSESSMENT_OCCURRENCE", String.valueOf(id), "Avaliação=" + assessmentId);
        return occurrence(id);
    }

    public List<OccurrenceView> occurrences(long assessmentId, Authentication authentication) {
        requireAssessmentRead(assessmentId, authentication);
        return jdbcTemplate.query(
                "select o.id, o.school_id, su.name school_name, o.class_id, sc.name class_name, o.occurred_at, o.description, o.created_by "
                        + "from network_assessment_occurrence o left join school_unit su on su.id = o.school_id left join school_class sc on sc.id = o.class_id "
                        + "where o.assessment_id = ? order by o.occurred_at desc, o.id desc",
                (rs, rowNum) -> new OccurrenceView(rs.getLong("id"), nullableLong(rs, "school_id"), rs.getString("school_name"), nullableLong(rs, "class_id"), rs.getString("class_name"), rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("description"), rs.getString("created_by")),
                assessmentId);
    }

    @Transactional
    public ImportSummary importAnswerSheets(long assessmentId, @Valid AnswerSheetImportRequest request, Authentication authentication) {
        accessService.requireNetworkWrite(authentication);
        loadAssessment(assessmentId);
        String sourceType = normalizeSourceType(request.sourceType());
        List<QuestionKey> questions = questionKeys(assessmentId);
        if (questions.isEmpty()) throw new IllegalArgumentException("Cadastre as questões e o gabarito oficial antes de importar respostas.");
        List<ImportItemResult> items = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        for (AnswerSheetPayload sheet : request.sheets()) {
            ImportItemResult item = storeSheet(assessmentId, sourceType, sheet, questions, authentication.getName());
            items.add(item);
            if (item.status().equals("VALID")) valid++; else invalid++;
        }
        if (valid > 0) jdbcTemplate.update("update network_assessment set status = 'APPLIED', updated_at = current_timestamp where id = ? and status in ('PREPARATION', 'READY', 'APPLIED')", assessmentId);
        auditService.record(authentication.getName(), "ASSESSMENT_ANSWER_SHEETS_IMPORT", "NETWORK_ASSESSMENT", String.valueOf(assessmentId),
                "Origem=" + sourceType + ", válidos=" + valid + ", inválidos=" + invalid);
        return new ImportSummary(request.sheets().size(), valid, invalid, items);
    }

    public ValidationSummary validation(long assessmentId, Authentication authentication) {
        accessService.requireNetworkProcess(authentication);
        loadAssessment(assessmentId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select validation_status, count(*) quantity from network_assessment_answer_sheet where assessment_id = ? and active_submission = true group by validation_status",
                rs -> counts.put(rs.getString("validation_status"), rs.getInt("quantity")), assessmentId);
        Integer rejected = jdbcTemplate.queryForObject("select count(*) from network_assessment_import_rejection where assessment_id = ?", Integer.class, assessmentId);
        int valid = counts.getOrDefault("VALID", 0);
        int invalid = counts.getOrDefault("INVALID", 0);
        int rejectedCount = rejected == null ? 0 : rejected;
        return new ValidationSummary(valid + invalid + rejectedCount, valid, invalid, rejectedCount);
    }

    @Transactional
    public ProcessingRunView process(long assessmentId, Authentication authentication) {
        accessService.requireNetworkProcess(authentication);
        loadAssessment(assessmentId);
        List<QuestionKey> questions = questionKeys(assessmentId);
        if (questions.isEmpty()) throw new IllegalArgumentException("Cadastre as questões e o gabarito oficial antes do processamento.");
        Integer validSheets = jdbcTemplate.queryForObject(
                "select count(*) from network_assessment_answer_sheet where assessment_id = ? and active_submission = true and validation_status = 'VALID'", Integer.class, assessmentId);
        Integer invalidSheets = jdbcTemplate.queryForObject(
                "select count(*) from network_assessment_answer_sheet where assessment_id = ? and active_submission = true and validation_status = 'INVALID'", Integer.class, assessmentId);
        if (validSheets == null || validSheets == 0) throw new IllegalArgumentException("Não existem gabaritos válidos para processar.");
        Integer runNumber = jdbcTemplate.queryForObject("select coalesce(max(run_number), 0) + 1 from network_assessment_processing_run where assessment_id = ?", Integer.class, assessmentId);
        jdbcTemplate.update("update network_assessment set status = 'PROCESSING', updated_at = current_timestamp where id = ?", assessmentId);
        Long runId = jdbcTemplate.queryForObject(
                "insert into network_assessment_processing_run (assessment_id, run_number, status, valid_sheets, invalid_sheets, initiated_by) values (?, ?, 'PROCESSING', ?, ?, ?) returning id",
                Long.class, assessmentId, runNumber, validSheets, invalidSheets == null ? 0 : invalidSheets, authentication.getName());

        List<SheetRow> sheets = jdbcTemplate.query(
                "select id, assignment_id from network_assessment_answer_sheet where assessment_id = ? and active_submission = true and validation_status = 'VALID' order by id",
                (rs, rowNum) -> new SheetRow(rs.getLong("id"), rs.getLong("assignment_id")), assessmentId);
        for (SheetRow sheet : sheets) {
            List<AnswerEvaluation> evaluations = jdbcTemplate.query(
                    "select q.descriptor, q.skill, case when upper(coalesce(a.selected_option, '')) = upper(q.correct_option) then true else false end correct "
                            + "from network_assessment_question q left join network_assessment_answer a on a.question_id = q.id and a.answer_sheet_id = ? "
                            + "where q.assessment_id = ? and q.active = true order by q.sequence_number",
                    (rs, rowNum) -> new AnswerEvaluation(rs.getString("descriptor"), rs.getString("skill"), rs.getBoolean("correct")), sheet.sheetId(), assessmentId);
            AssessmentResultCalculator.Result result = resultCalculator.calculate(evaluations);
            Long resultId = jdbcTemplate.queryForObject(
                    "insert into network_assessment_result (processing_run_id, assessment_id, assignment_id, correct_answers, total_questions, score_percent) values (?, ?, ?, ?, ?, ?) returning id",
                    Long.class, runId, assessmentId, sheet.assignmentId(), result.correctAnswers(), result.totalQuestions(), result.scorePercent());
            for (AssessmentResultCalculator.SkillResult skill : result.skills()) {
                jdbcTemplate.update(
                        "insert into network_assessment_result_skill (result_id, descriptor, skill, correct_answers, total_questions, score_percent) values (?, ?, ?, ?, ?, ?)",
                        resultId, skill.descriptor(), skill.skill(), skill.correctAnswers(), skill.totalQuestions(), skill.scorePercent());
            }
        }
        jdbcTemplate.update("update network_assessment_processing_run set status = 'COMPLETED', completed_at = current_timestamp where id = ?", runId);
        jdbcTemplate.update("update network_assessment set status = 'PROCESSED', updated_at = current_timestamp where id = ?", assessmentId);
        auditService.record(authentication.getName(), runNumber != null && runNumber > 1 ? "ASSESSMENT_REPROCESS" : "ASSESSMENT_PROCESS", "NETWORK_ASSESSMENT", String.valueOf(assessmentId),
                "Execução=" + runNumber + ", gabaritos válidos=" + validSheets + ", inválidos=" + (invalidSheets == null ? 0 : invalidSheets));
        return processingRun(runId);
    }

    public List<ProcessingRunView> processingHistory(long assessmentId, Authentication authentication) {
        requireAssessmentRead(assessmentId, authentication);
        return jdbcTemplate.query(
                "select id, run_number, status, valid_sheets, invalid_sheets, initiated_by, started_at, completed_at from network_assessment_processing_run where assessment_id = ? order by run_number desc",
                processingRunMapper(), assessmentId);
    }

    public List<ResultSummaryRow> results(long assessmentId, String level, Long schoolId, Long classId, Long studentId, Authentication authentication) {
        String normalizedLevel = level == null || level.isBlank() ? "NETWORK" : level.trim().toUpperCase(Locale.ROOT);
        if (!RESULT_LEVELS.contains(normalizedLevel)) throw new IllegalArgumentException("Nível de resultado inválido.");
        Long runId = latestRunId(assessmentId);
        if (runId == null) return List.of();
        List<Object> parameters = new ArrayList<>();
        String labelSelect;
        String groupBy;
        switch (normalizedLevel) {
            case "NETWORK" -> {
                accessService.requireResultRead(authentication, null);
                labelSelect = "0 key_id, 'Rede municipal' label";
                groupBy = "";
            }
            case "SCHOOL" -> {
                labelSelect = "aa.school_id key_id, su.name label";
                groupBy = " group by aa.school_id, su.name";
            }
            case "CLASS" -> {
                labelSelect = "aa.class_id key_id, sc.name label";
                groupBy = " group by aa.class_id, sc.name";
            }
            case "STUDENT" -> {
                labelSelect = "aa.student_id key_id, st.name label";
                groupBy = " group by aa.student_id, st.name";
            }
            default -> throw new IllegalArgumentException("Nível de resultado inválido.");
        }
        StringBuilder sql = new StringBuilder("select ").append(labelSelect)
                .append(", count(distinct aa.student_id) students, sum(r.correct_answers) correct_answers, sum(r.total_questions) total_questions ")
                .append("from network_assessment_result r join network_assessment_assignment aa on aa.id = r.assignment_id ")
                .append("join school_unit su on su.id = aa.school_id join school_class sc on sc.id = aa.class_id join student st on st.id = aa.student_id ")
                .append("where r.processing_run_id = ? and r.assessment_id = ?");
        parameters.add(runId); parameters.add(assessmentId);
        appendResultScope(sql, parameters, schoolId, classId, studentId, authentication);
        sql.append(groupBy).append(" order by label");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            long correct = rs.getLong("correct_answers");
            long total = rs.getLong("total_questions");
            return new ResultSummaryRow(rs.getLong("key_id"), rs.getString("label"), rs.getLong("students"), correct, total, percentage(correct, total));
        }, parameters.toArray());
    }

    public List<SkillSummaryRow> skillResults(long assessmentId, Long schoolId, Long classId, Long studentId, Authentication authentication) {
        Long runId = latestRunId(assessmentId);
        if (runId == null) return List.of();
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "select rs.descriptor, rs.skill, sum(rs.correct_answers) correct_answers, sum(rs.total_questions) total_questions "
                        + "from network_assessment_result_skill rs join network_assessment_result r on r.id = rs.result_id "
                        + "join network_assessment_assignment aa on aa.id = r.assignment_id where r.processing_run_id = ? and r.assessment_id = ?");
        parameters.add(runId); parameters.add(assessmentId);
        appendResultScope(sql, parameters, schoolId, classId, studentId, authentication);
        sql.append(" group by rs.descriptor, rs.skill order by rs.descriptor, rs.skill");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            long correct = rs.getLong("correct_answers");
            long total = rs.getLong("total_questions");
            return new SkillSummaryRow(rs.getString("descriptor"), rs.getString("skill"), correct, total, percentage(correct, total));
        }, parameters.toArray());
    }

    private void appendResultScope(StringBuilder sql, List<Object> parameters, Long schoolId, Long classId, Long studentId, Authentication authentication) {
        if (schoolId != null) {
            accessService.requireResultRead(authentication, schoolId);
            sql.append(" and aa.school_id = ?"); parameters.add(schoolId);
        } else if (!accessService.hasNetworkResultRead(authentication)) {
            List<Long> ids = accessService.resultSchoolIds(authentication);
            if (ids.isEmpty()) throw new AccessDeniedException("Sua conta não possui unidade escolar autorizada para consultar resultados.");
            sql.append(" and aa.school_id in (").append(placeholders(ids.size())).append(")"); parameters.addAll(ids);
        }
        if (classId != null) { sql.append(" and aa.class_id = ?"); parameters.add(classId); }
        if (studentId != null) { sql.append(" and aa.student_id = ?"); parameters.add(studentId); }
    }

    private ImportItemResult storeSheet(long assessmentId, String sourceType, AnswerSheetPayload sheet, List<QuestionKey> questions, String actor) {
        String payload = serialize(sheet);
        String sourceHash = hash(payload);
        AssignmentKey assignment = resolveAssignment(assessmentId, sheet);
        String identifier = identifier(sheet);
        if (assignment == null) {
            String reason = "Não foi possível associar o gabarito a um estudante organizado nesta avaliação.";
            jdbcTemplate.update(
                    "insert into network_assessment_import_rejection (assessment_id, source_type, source_payload, source_hash, rejection_reason, submitted_by) values (?, ?, ?, ?, ?, ?)",
                    assessmentId, sourceType, payload, sourceHash, reason, actor);
            return new ImportItemResult(identifier, "INVALID", reason, null);
        }

        Map<Integer, QuestionKey> bySequence = new LinkedHashMap<>();
        questions.forEach(question -> bySequence.put(question.sequenceNumber(), question));
        Map<Integer, String> answers = sheet.answers() == null ? Map.of() : sheet.answers();
        List<String> problems = new ArrayList<>();
        for (QuestionKey question : questions) {
            String answer = answers.get(question.sequenceNumber());
            if (answer == null || answer.isBlank()) problems.add("questão " + question.sequenceNumber() + " sem resposta");
        }
        for (Integer sequence : answers.keySet()) {
            if (!bySequence.containsKey(sequence)) problems.add("questão " + sequence + " não pertence à avaliação");
        }
        String status = problems.isEmpty() ? "VALID" : "INVALID";
        String message = problems.isEmpty() ? "Gabarito válido para processamento." : String.join("; ", problems);

        jdbcTemplate.update("update network_assessment_answer_sheet set active_submission = false where assessment_id = ? and assignment_id = ? and active_submission = true", assessmentId, assignment.assignmentId());
        Long sheetId = jdbcTemplate.queryForObject(
                "insert into network_assessment_answer_sheet (assessment_id, assignment_id, source_type, source_payload, source_hash, validation_status, validation_message, submitted_by) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, assessmentId, assignment.assignmentId(), sourceType, payload, sourceHash, status, message, actor);
        for (Map.Entry<Integer, String> answer : answers.entrySet()) {
            QuestionKey question = bySequence.get(answer.getKey());
            if (question != null) {
                jdbcTemplate.update("insert into network_assessment_answer (answer_sheet_id, question_id, selected_option) values (?, ?, ?)",
                        sheetId, question.questionId(), blankToNull(answer.getValue() == null ? null : answer.getValue().trim().toUpperCase(Locale.ROOT)));
            }
        }
        if (sourceType.equals("ONLINE")) {
            jdbcTemplate.update("update network_assessment_assignment set attendance_status = 'MAKEUP' where id = ?", assignment.assignmentId());
        }
        return new ImportItemResult(identifier, status, message, sheetId);
    }

    private AssignmentKey resolveAssignment(long assessmentId, AnswerSheetPayload sheet) {
        if (sheet.onlineAccessCode() != null && !sheet.onlineAccessCode().isBlank()) {
            List<AssignmentKey> rows = jdbcTemplate.query(
                    "select id, student_id from network_assessment_assignment where assessment_id = ? and online_access_code = ?",
                    (rs, rowNum) -> new AssignmentKey(rs.getLong("id"), rs.getLong("student_id")), assessmentId, sheet.onlineAccessCode().trim());
            if (!rows.isEmpty()) return rows.getFirst();
        }
        if (sheet.labelCode() != null && !sheet.labelCode().isBlank()) {
            List<AssignmentKey> rows = jdbcTemplate.query(
                    "select id, student_id from network_assessment_assignment where assessment_id = ? and label_code = ?",
                    (rs, rowNum) -> new AssignmentKey(rs.getLong("id"), rs.getLong("student_id")), assessmentId, sheet.labelCode().trim());
            if (!rows.isEmpty()) return rows.getFirst();
        }
        if (sheet.registration() != null && !sheet.registration().isBlank()) {
            List<AssignmentKey> rows = jdbcTemplate.query(
                    "select aa.id, aa.student_id from network_assessment_assignment aa join student s on s.id = aa.student_id where aa.assessment_id = ? and s.registration = ?",
                    (rs, rowNum) -> new AssignmentKey(rs.getLong("id"), rs.getLong("student_id")), assessmentId, sheet.registration().trim());
            if (!rows.isEmpty()) return rows.getFirst();
        }
        return null;
    }

    private String identifier(AnswerSheetPayload sheet) {
        if (sheet.registration() != null && !sheet.registration().isBlank()) return sheet.registration().trim();
        if (sheet.labelCode() != null && !sheet.labelCode().isBlank()) return sheet.labelCode().trim();
        if (sheet.onlineAccessCode() != null && !sheet.onlineAccessCode().isBlank()) return "Acesso online";
        return "Sem identificação";
    }

    private List<AssignmentView> assignmentsForArtifact(long assessmentId, Long schoolId, Long classId) {
        List<Object> params = new ArrayList<>(); params.add(assessmentId);
        StringBuilder sql = new StringBuilder(
                "select aa.id, aa.student_id, s.registration, s.name student_name, aa.school_id, su.name school_name, aa.class_id, sc.name class_name, aa.attendance_status, aa.label_code, aa.package_code "
                        + "from network_assessment_assignment aa join student s on s.id = aa.student_id join school_unit su on su.id = aa.school_id join school_class sc on sc.id = aa.class_id where aa.assessment_id = ?");
        if (schoolId != null) { sql.append(" and aa.school_id = ?"); params.add(schoolId); }
        if (classId != null) { sql.append(" and aa.class_id = ?"); params.add(classId); }
        sql.append(" order by su.name, sc.name, s.name");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AssignmentView(rs.getLong("id"), rs.getLong("student_id"), rs.getString("registration"), rs.getString("student_name"), rs.getLong("school_id"), rs.getString("school_name"), rs.getLong("class_id"), rs.getString("class_name"), rs.getString("attendance_status"), rs.getString("label_code"), rs.getString("package_code")), params.toArray());
    }

    private List<QuestionKey> questionKeys(long assessmentId) {
        return jdbcTemplate.query(
                "select id, sequence_number, descriptor, skill, correct_option from network_assessment_question where assessment_id = ? and active = true order by sequence_number",
                (rs, rowNum) -> new QuestionKey(rs.getLong("id"), rs.getInt("sequence_number"), rs.getString("descriptor"), rs.getString("skill"), rs.getString("correct_option")), assessmentId);
    }

    private void requireAssessmentRead(long assessmentId, Authentication authentication) {
        loadAssessment(assessmentId);
        if (accessService.hasNetworkRead(authentication)) return;
        List<Long> schoolIds = accessService.readableSchoolIds(authentication);
        if (schoolIds.isEmpty()) throw new AccessDeniedException("Sua conta não possui acesso a esta avaliação.");
        List<Object> params = new ArrayList<>(); params.add(assessmentId); params.addAll(schoolIds);
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from network_assessment_scope where assessment_id = ? and school_id in (" + placeholders(schoolIds.size()) + ")",
                Integer.class, params.toArray());
        if (count == null || count == 0) throw new AccessDeniedException("Sua conta não possui acesso a esta avaliação.");
    }

    private AssessmentView loadAssessment(long assessmentId) {
        List<AssessmentView> rows = jdbcTemplate.query(assessmentSelect() + " where a.id = ?", assessmentMapper(), assessmentId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Avaliação em Rede não encontrada.");
        return rows.getFirst();
    }

    private String assessmentSelect() {
        return "select a.id, a.name, a.stage, a.academic_year, a.grade_stage, a.component_id, cc.name component_name, a.status, a.application_manual, a.instructions, "
                + "(select count(distinct sx.school_id) from network_assessment_scope sx where sx.assessment_id = a.id) school_count, "
                + "(select count(*) from network_assessment_scope sx where sx.assessment_id = a.id) class_count, "
                + "(select count(*) from network_assessment_assignment ax where ax.assessment_id = a.id) student_count, "
                + "(select count(*) from network_assessment_question qx where qx.assessment_id = a.id and qx.active = true) question_count "
                + "from network_assessment a left join curricular_component cc on cc.id = a.component_id";
    }

    private org.springframework.jdbc.core.RowMapper<AssessmentView> assessmentMapper() {
        return (rs, rowNum) -> new AssessmentView(rs.getLong("id"), rs.getString("name"), rs.getString("stage"), rs.getInt("academic_year"),
                rs.getString("grade_stage"), nullableLong(rs, "component_id"), rs.getString("component_name"), rs.getString("status"),
                rs.getString("application_manual"), rs.getString("instructions"), rs.getInt("school_count"), rs.getInt("class_count"),
                rs.getInt("student_count"), rs.getInt("question_count"));
    }

    private ProcessingRunView processingRun(long runId) {
        List<ProcessingRunView> rows = jdbcTemplate.query(
                "select id, run_number, status, valid_sheets, invalid_sheets, initiated_by, started_at, completed_at from network_assessment_processing_run where id = ?",
                processingRunMapper(), runId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Execução de processamento não encontrada.");
        return rows.getFirst();
    }

    private org.springframework.jdbc.core.RowMapper<ProcessingRunView> processingRunMapper() {
        return (rs, rowNum) -> new ProcessingRunView(rs.getLong("id"), rs.getInt("run_number"), rs.getString("status"), rs.getInt("valid_sheets"),
                rs.getInt("invalid_sheets"), rs.getString("initiated_by"), rs.getObject("started_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class));
    }

    private OccurrenceView occurrence(long id) {
        return jdbcTemplate.query(
                "select o.id, o.school_id, su.name school_name, o.class_id, sc.name class_name, o.occurred_at, o.description, o.created_by from network_assessment_occurrence o left join school_unit su on su.id = o.school_id left join school_class sc on sc.id = o.class_id where o.id = ?",
                (rs, rowNum) -> new OccurrenceView(rs.getLong("id"), nullableLong(rs, "school_id"), rs.getString("school_name"), nullableLong(rs, "class_id"), rs.getString("class_name"), rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("description"), rs.getString("created_by")), id).getFirst();
    }

    private ClassScope classScope(long classId) {
        List<ClassScope> rows = jdbcTemplate.query("select id, school_id, name, stage, academic_year from school_class where id = ? and active = true",
                (rs, rowNum) -> new ClassScope(rs.getLong("id"), rs.getLong("school_id"), rs.getString("name"), rs.getString("stage"), rs.getInt("academic_year")), classId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Turma ativa não encontrada.");
        return rows.getFirst();
    }

    private List<ComponentOption> components() {
        return jdbcTemplate.query("select id, code, name from curricular_component where active = true order by name",
                (rs, rowNum) -> new ComponentOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
    }

    private void requireComponent(long componentId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from curricular_component where id = ? and active = true", Integer.class, componentId);
        if (count == null || count == 0) throw new IllegalArgumentException("Componente curricular não encontrado ou inativo.");
    }

    private Long latestRunId(long assessmentId) {
        List<Long> ids = jdbcTemplate.query(
                "select id from network_assessment_processing_run where assessment_id = ? and status = 'COMPLETED' order by run_number desc limit 1",
                (rs, rowNum) -> rs.getLong("id"), assessmentId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private String normalizeStage(String stage) {
        String normalized = stage == null ? "" : stage.trim().toUpperCase(Locale.ROOT);
        if (!STAGES.contains(normalized)) throw new IllegalArgumentException("Etapa inválida. Use DIAGNOSTIC, MONITORING ou FINAL.");
        return normalized;
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(normalized)) throw new IllegalArgumentException("Origem inválida. Use MANUAL, IMPORT ou ONLINE.");
        return normalized;
    }

    private void validateAcademicYear(Integer academicYear) {
        if (academicYear == null || academicYear < 2000 || academicYear > 2200) throw new IllegalArgumentException("Ano letivo inválido.");
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Não foi possível preservar os dados de origem do gabarito.");
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) return null;
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record AssessmentRequest(@NotBlank(message = "Informe o nome da avaliação.") String name,
            @NotBlank(message = "Informe a etapa da avaliação.") String stage,
            @NotNull(message = "Informe o ano letivo.") Integer academicYear,
            @NotBlank(message = "Informe a etapa/ano-série.") String gradeStage,
            Long componentId, String applicationManual, String instructions) {}

    public record QuestionRequest(@Min(value = 1, message = "A numeração da questão deve começar em 1.") int sequenceNumber,
            @NotBlank(message = "Informe o descritor da questão.") String descriptor,
            @NotBlank(message = "Informe a habilidade da questão.") String skill,
            @NotBlank(message = "Informe a alternativa correta.") String correctOption) {}

    public record OrganizeRequest(@NotEmpty(message = "Selecione ao menos uma turma.") List<@NotNull Long> classIds) {}
    public record AttendanceRequest(@NotBlank(message = "Informe a situação de presença.") String status) {}
    public record OccurrenceRequest(Long schoolId, Long classId, @NotBlank(message = "Descreva a ocorrência.") String description) {}
    public record AnswerSheetImportRequest(@NotBlank(message = "Informe a origem dos gabaritos.") String sourceType,
            @NotEmpty(message = "Informe ao menos um gabarito.") List<@Valid AnswerSheetPayload> sheets) {}
    public record AnswerSheetPayload(String registration, String labelCode, String onlineAccessCode,
            @NotNull(message = "Informe as respostas do gabarito.") Map<Integer, String> answers) {}

    public record AssessmentView(long id, String name, String stage, int academicYear, String gradeStage, Long componentId,
            String componentName, String status, String applicationManual, String instructions, int schoolCount, int classCount,
            int studentCount, int questionCount) {}
    public record QuestionView(long id, int sequenceNumber, String descriptor, String skill, String correctOption) {}
    public record OrganizationSummary(int schools, int classes, int students) {}
    public record AssignmentView(long id, long studentId, String registration, String studentName, long schoolId, String schoolName,
            long classId, String className, String attendanceStatus, String labelCode, String packageCode) {}
    public record ArtifactView(String type, String title, OffsetDateTime generatedAt, int lineCount, List<String> lines) {}
    public record OccurrenceView(long id, Long schoolId, String schoolName, Long classId, String className, OffsetDateTime occurredAt,
            String description, String createdBy) {}
    public record ImportItemResult(String identifier, String status, String message, Long answerSheetId) {}
    public record ImportSummary(int recordsRead, int valid, int invalid, List<ImportItemResult> items) {}
    public record ValidationSummary(int recordsRead, int valid, int invalid, int associationRejected) {}
    public record ProcessingRunView(long id, int runNumber, String status, int validSheets, int invalidSheets, String initiatedBy,
            OffsetDateTime startedAt, OffsetDateTime completedAt) {}
    public record ResultSummaryRow(long keyId, String label, long students, long correctAnswers, long totalQuestions, BigDecimal scorePercent) {}
    public record SkillSummaryRow(String descriptor, String skill, long correctAnswers, long totalQuestions, BigDecimal scorePercent) {}
    public record CatalogView(List<SchoolOption> schools, List<ClassOption> classes, List<ComponentOption> components) {}
    public record SchoolOption(long id, String name) {}
    public record ClassOption(long id, long schoolId, String name, String stage, int academicYear) {}
    public record ComponentOption(long id, String code, String name) {}

    private record ClassScope(long classId, long schoolId, String className, String stage, int academicYear) {}
    private record EnrollmentRow(long enrollmentId, long studentId) {}
    private record QuestionKey(long questionId, int sequenceNumber, String descriptor, String skill, String correctOption) {}
    private record SheetRow(long sheetId, long assignmentId) {}
    private record AssignmentKey(long assignmentId, long studentId) {}
}
