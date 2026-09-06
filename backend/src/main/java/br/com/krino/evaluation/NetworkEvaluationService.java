package br.com.krino.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.security.KrinoUserPrincipal;

@Service
public class NetworkEvaluationService {

    private static final Set<String> STAGES = Set.of("DIAGNOSTIC", "MONITORING", "FINAL");
    private static final Set<String> STATUSES = Set.of("PREPARATION", "OPEN", "CLOSED");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final NetworkEvaluationAccessService accessService;
    private final SecurityAuditService auditService;

    public NetworkEvaluationService(JdbcTemplate jdbcTemplate, NetworkEvaluationAccessService accessService, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    public EvaluationContext context(Authentication authentication) {
        List<Long> schoolIds = accessService.accessibleSchoolIds(authentication);
        if (schoolIds.isEmpty()) return new EvaluationContext(List.of(), List.of());
        String placeholders = String.join(",", java.util.Collections.nCopies(schoolIds.size(), "?"));
        List<SchoolOption> schools = jdbcTemplate.query(
                "select id, code, name from school_unit where id in (" + placeholders + ") and active = true order by name",
                (rs, rowNum) -> new SchoolOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")), schoolIds.toArray());
        List<ComponentOption> components = jdbcTemplate.query(
                "select id, code, name from curricular_component where active = true order by name",
                (rs, rowNum) -> new ComponentOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
        return new EvaluationContext(schools, components);
    }

    public List<ClassOption> classes(long schoolId, int academicYear, Authentication authentication) {
        accessService.requireResultRead(authentication, schoolId);
        return jdbcTemplate.query(
                "select id, name, stage, shift from school_class where school_id = ? and academic_year = ? and active = true order by name",
                (rs, rowNum) -> new ClassOption(rs.getLong("id"), rs.getString("name"), rs.getString("stage"), rs.getString("shift")), schoolId, academicYear);
    }

    public List<EvaluationView> list(Integer academicYear, String stage, Authentication authentication) {
        List<Long> schoolIds = accessService.accessibleSchoolIds(authentication);
        if (schoolIds.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder(baseEvaluationSelect())
                .append(" where exists (select 1 from network_evaluation_class ec2 join school_class c2 on c2.id = ec2.class_id where ec2.evaluation_id = e.id and c2.school_id in (")
                .append(String.join(",", java.util.Collections.nCopies(schoolIds.size(), "?"))).append("))");
        List<Object> args = new ArrayList<>(schoolIds);
        if (academicYear != null) { sql.append(" and e.academic_year = ?"); args.add(academicYear); }
        if (stage != null && !stage.isBlank()) { sql.append(" and e.evaluation_stage = ?"); args.add(normalizeStage(stage)); }
        sql.append(" order by e.academic_year desc, e.application_date desc nulls last, e.id desc");
        return jdbcTemplate.query(sql.toString(), this::mapEvaluation, args.toArray());
    }

    public EvaluationView get(long evaluationId, Authentication authentication) {
        accessService.requireEvaluationRead(authentication, evaluationId);
        return requireEvaluation(evaluationId);
    }

    @Transactional
    public EvaluationView create(EvaluationInput input, Authentication authentication) {
        accessService.requireNetworkManage(authentication);
        validateEvaluationInput(input);
        long actorId = userId(authentication);
        Long id = jdbcTemplate.queryForObject(
                "insert into network_evaluation (name, evaluation_stage, academic_year, grade_stage, status, application_date, materials_received_at, applicator_instructions, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, clean(input.name()), normalizeStage(input.stage()), input.academicYear(), clean(input.gradeStage()), normalizeStatus(input.status()),
                nullableDate(input.applicationDate()), nullableDate(input.materialsReceivedAt()), blankToNull(input.applicatorInstructions()), actorId);
        if (id == null) throw new IllegalStateException("Não foi possível criar a avaliação.");
        replaceClasses(id, input.classIds());
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_CREATED", "NETWORK_EVALUATION", Long.toString(id), "Avaliação em Rede criada.");
        return requireEvaluation(id);
    }

    @Transactional
    public EvaluationView update(long evaluationId, EvaluationInput input, Authentication authentication) {
        accessService.requireNetworkManage(authentication);
        requireEvaluation(evaluationId);
        validateEvaluationInput(input);
        Integer runs = jdbcTemplate.queryForObject("select count(*) from network_evaluation_processing_run where evaluation_id = ?", Integer.class, evaluationId);
        if (runs != null && runs > 0 && !sameConfiguredClasses(evaluationId, input.classIds())) {
            throw new IllegalArgumentException("As turmas não podem ser alteradas depois que a avaliação possui resultados processados.");
        }
        jdbcTemplate.update(
                "update network_evaluation set name = ?, evaluation_stage = ?, academic_year = ?, grade_stage = ?, status = ?, application_date = ?, materials_received_at = ?, applicator_instructions = ?, updated_at = current_timestamp where id = ?",
                clean(input.name()), normalizeStage(input.stage()), input.academicYear(), clean(input.gradeStage()), normalizeStatus(input.status()), nullableDate(input.applicationDate()), nullableDate(input.materialsReceivedAt()), blankToNull(input.applicatorInstructions()), evaluationId);
        if (runs == null || runs == 0) replaceClasses(evaluationId, input.classIds());
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_UPDATED", "NETWORK_EVALUATION", Long.toString(evaluationId), "Parametrização da avaliação atualizada.");
        return requireEvaluation(evaluationId);
    }

    @Transactional
    public ItemView addItem(long evaluationId, ItemInput input, Authentication authentication) {
        accessService.requireNetworkManage(authentication);
        EvaluationView evaluation = requireEvaluation(evaluationId);
        if (!evaluation.status().equals("PREPARATION")) throw new IllegalArgumentException("Itens só podem ser alterados enquanto a avaliação estiver em preparação.");
        if (input.itemNumber() == null || input.itemNumber() <= 0) throw new IllegalArgumentException("Informe um número de questão válido.");
        String correctAnswer = clean(input.correctAnswer()).toUpperCase(Locale.ROOT);
        BigDecimal maxScore = input.maxScore() == null ? BigDecimal.ONE : input.maxScore();
        if (maxScore.signum() <= 0) throw new IllegalArgumentException("A pontuação máxima da questão deve ser maior que zero.");
        requireComponent(input.componentId());
        Long id = jdbcTemplate.queryForObject(
                "insert into network_evaluation_item (evaluation_id, item_number, component_id, prompt_text, correct_answer, max_score, skill_code, skill_label, descriptor_code, descriptor_label) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (evaluation_id, item_number) do update set component_id = excluded.component_id, prompt_text = excluded.prompt_text, correct_answer = excluded.correct_answer, max_score = excluded.max_score, skill_code = excluded.skill_code, skill_label = excluded.skill_label, descriptor_code = excluded.descriptor_code, descriptor_label = excluded.descriptor_label returning id",
                Long.class, evaluationId, input.itemNumber(), input.componentId(), blankToNull(input.promptText()), correctAnswer, maxScore,
                blankToNull(input.skillCode()), blankToNull(input.skillLabel()), blankToNull(input.descriptorCode()), blankToNull(input.descriptorLabel()));
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_ITEM_SAVED", "NETWORK_EVALUATION", Long.toString(evaluationId), "Questão " + input.itemNumber() + " configurada.");
        return requireItem(id);
    }

    public List<ItemView> items(long evaluationId, Authentication authentication) {
        accessService.requireEvaluationRead(authentication, evaluationId);
        return items(evaluationId);
    }

    @Transactional
    public OrganizationSummary organize(long evaluationId, Authentication authentication) {
        accessService.requireNetworkManage(authentication);
        EvaluationView evaluation = requireEvaluation(evaluationId);
        Integer classCount = jdbcTemplate.queryForObject("select count(*) from network_evaluation_class where evaluation_id = ?", Integer.class, evaluationId);
        if (classCount == null || classCount == 0) throw new IllegalArgumentException("Vincule pelo menos uma turma antes de organizar os estudantes.");
        int inserted = jdbcTemplate.update(
                "insert into network_evaluation_student (evaluation_id, enrollment_id, student_id, school_id, class_id) "
                        + "select ?, en.id, en.student_id, c.school_id, c.id from network_evaluation_class ec join school_class c on c.id = ec.class_id "
                        + "join student_enrollment en on en.class_id = c.id and en.academic_year = ? and en.status = 'ACTIVE' "
                        + "join student s on s.id = en.student_id and s.status = 'ACTIVE' "
                        + "where ec.evaluation_id = ? and c.academic_year = ? and c.stage = ? "
                        + "on conflict (evaluation_id, student_id) do nothing",
                evaluationId, evaluation.academicYear(), evaluationId, evaluation.academicYear(), evaluation.gradeStage());
        int total = countStudents(evaluationId);
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_ORGANIZED", "NETWORK_EVALUATION", Long.toString(evaluationId), "Organização atualizada; novos estudantes: " + inserted + ".");
        return new OrganizationSummary(total, inserted, countClasses(evaluationId));
    }

    public List<StudentView> students(long evaluationId, Long schoolId, Long classId, Authentication authentication) {
        accessService.requireEvaluationRead(authentication, evaluationId);
        StringBuilder sql = new StringBuilder(
                "select es.id, es.student_id, es.school_id, es.class_id, s.registration, s.name student_name, su.name school_name, c.name class_name, es.organization_status, es.online_token_expires_at, es.online_completed_at "
                        + "from network_evaluation_student es join student s on s.id = es.student_id join school_unit su on su.id = es.school_id join school_class c on c.id = es.class_id where es.evaluation_id = ?");
        List<Object> args = new ArrayList<>(); args.add(evaluationId);
        if (schoolId != null) { accessService.requireResultRead(authentication, schoolId); sql.append(" and es.school_id = ?"); args.add(schoolId); }
        if (classId != null) { sql.append(" and es.class_id = ?"); args.add(classId); }
        sql.append(" order by su.name, c.name, s.name");
        return jdbcTemplate.query(sql.toString(), this::mapStudent, args.toArray());
    }

    public MaterialsView materials(long evaluationId, long classId, Authentication authentication) {
        accessService.requireEvaluationRead(authentication, evaluationId);
        EvaluationView evaluation = requireEvaluation(evaluationId);
        ClassSnapshot classSnapshot = requireEvaluationClass(evaluationId, classId);
        accessService.requireResultRead(authentication, classSnapshot.schoolId());
        List<StudentView> students = students(evaluationId, classSnapshot.schoolId(), classId, authentication);
        List<AttendanceRow> attendance = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            StudentView student = students.get(i);
            attendance.add(new AttendanceRow(i + 1, student.registration(), student.name(), student.organizationStatus()));
        }
        String label = evaluation.name() + " | " + classSnapshot.schoolName() + " | " + classSnapshot.className() + " | " + evaluation.stageLabel() + " | " + evaluation.academicYear();
        String manual = evaluation.applicatorInstructions() == null || evaluation.applicatorInstructions().isBlank()
                ? "Confirme a identificação da turma e dos estudantes, registre presença e ocorrências e preserve o sigilo do instrumento até a aplicação."
                : evaluation.applicatorInstructions();
        return new MaterialsView(evaluation, classSnapshot.schoolName(), classSnapshot.className(), attendance, label, manual, occurrences(evaluationId, classId, authentication));
    }

    @Transactional
    public OccurrenceView addOccurrence(long evaluationId, long classId, OccurrenceInput input, Authentication authentication) {
        accessService.requireNetworkManage(authentication);
        requireEvaluationClass(evaluationId, classId);
        if (input == null || input.occurredAt() == null) throw new IllegalArgumentException("Informe a data e hora da ocorrência.");
        String description = clean(input.description());
        if (description.length() > 2000) throw new IllegalArgumentException("A ocorrência deve possuir no máximo 2000 caracteres.");
        Long id = jdbcTemplate.queryForObject("insert into network_evaluation_occurrence (evaluation_id, class_id, occurred_at, description, created_by) values (?, ?, ?, ?, ?) returning id",
                Long.class, evaluationId, classId, input.occurredAt(), description, userId(authentication));
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_OCCURRENCE_RECORDED", "NETWORK_EVALUATION", Long.toString(evaluationId), "Ocorrência registrada para a turma " + classId + ".");
        return requireOccurrence(id);
    }

    public List<OccurrenceView> occurrences(long evaluationId, long classId, Authentication authentication) {
        accessService.requireEvaluationRead(authentication, evaluationId);
        return jdbcTemplate.query(
                "select o.id, o.occurred_at, o.description, u.display_name created_by_name from network_evaluation_occurrence o join app_user u on u.id = o.created_by where o.evaluation_id = ? and o.class_id = ? order by o.occurred_at, o.id",
                (rs, rowNum) -> new OccurrenceView(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("description"), rs.getString("created_by_name")), evaluationId, classId);
    }

    @Transactional
    public OnlineTokenView issueOnlineToken(long evaluationId, long evaluationStudentId, OnlineTokenInput input, Authentication authentication) {
        accessService.requireNetworkManage(authentication);
        EvaluationView evaluation = requireEvaluation(evaluationId);
        if (!evaluation.status().equals("OPEN")) throw new IllegalArgumentException("Abra a avaliação antes de liberar uma segunda chamada online.");
        StudentView student = requireStudent(evaluationId, evaluationStudentId);
        OffsetDateTime expiresAt = input == null ? null : input.expiresAt();
        if (expiresAt == null || !expiresAt.isAfter(OffsetDateTime.now())) throw new IllegalArgumentException("Informe uma validade futura para o acesso online.");
        byte[] random = new byte[32]; SECURE_RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbcTemplate.update("update network_evaluation_student set online_token_hash = ?, online_token_expires_at = ?, online_completed_at = null where id = ? and evaluation_id = ?",
                sha256(token), expiresAt, evaluationStudentId, evaluationId);
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_ONLINE_ACCESS_ISSUED", "NETWORK_EVALUATION_STUDENT", Long.toString(evaluationStudentId), "Acesso de segunda chamada online emitido até " + expiresAt + ".");
        return new OnlineTokenView(token, expiresAt, student.name(), evaluation.name());
    }

    public OnlineEvaluationView online(String token) {
        String hash = sha256(requireToken(token));
        List<OnlineStudentCore> rows = jdbcTemplate.query(
                "select es.id evaluation_student_id, es.online_token_expires_at, es.online_completed_at, e.id evaluation_id, e.name evaluation_name, e.status evaluation_status, s.name student_name "
                        + "from network_evaluation_student es join network_evaluation e on e.id = es.evaluation_id join student s on s.id = es.student_id where es.online_token_hash = ?",
                (rs, rowNum) -> new OnlineStudentCore(rs.getLong("evaluation_student_id"), rs.getLong("evaluation_id"), rs.getString("evaluation_name"), rs.getString("evaluation_status"), rs.getString("student_name"), rs.getObject("online_token_expires_at", OffsetDateTime.class), rs.getObject("online_completed_at", OffsetDateTime.class)), hash);
        if (rows.isEmpty()) throw new IllegalArgumentException("O acesso informado é inválido.");
        OnlineStudentCore core = rows.getFirst();
        if (!core.status().equals("OPEN")) throw new IllegalArgumentException("Esta avaliação online não está aberta.");
        if (core.completedAt() != null) throw new IllegalArgumentException("Esta segunda chamada já foi enviada.");
        if (core.expiresAt() == null || core.expiresAt().isBefore(OffsetDateTime.now())) throw new IllegalArgumentException("O acesso à segunda chamada expirou.");
        List<OnlineItemView> onlineItems = jdbcTemplate.query(
                "select i.id, i.item_number, i.prompt_text, cc.name component_name from network_evaluation_item i join curricular_component cc on cc.id = i.component_id where i.evaluation_id = ? order by i.item_number",
                (rs, rowNum) -> new OnlineItemView(rs.getLong("id"), rs.getInt("item_number"), rs.getString("component_name"), rs.getString("prompt_text")), core.evaluationId());
        if (onlineItems.isEmpty()) throw new IllegalArgumentException("A avaliação ainda não possui questões configuradas.");
        return new OnlineEvaluationView(core.evaluationId(), core.evaluationStudentId(), core.evaluationName(), core.studentName(), core.expiresAt(), onlineItems);
    }

    @Transactional
    public BatchView submitOnline(String token, OnlineSubmission input) {
        OnlineEvaluationView online = online(token);
        List<ItemView> itemViews = items(online.evaluationId());
        if (input == null || input.answers() == null || input.answers().size() != itemViews.size()) throw new IllegalArgumentException("Responda todas as questões antes de enviar.");
        String registration = registrationForEvaluationStudent(online.evaluationStudentId());
        String rawAnswers = normalizeAnswers(input.answers(), itemViews.size());
        Long batchId = jdbcTemplate.queryForObject(
                "insert into network_evaluation_answer_batch (evaluation_id, source_type, raw_source, state, total_records, valid_records, invalid_records) values (?, 'ONLINE', ?, 'NOT_PROCESSED', 1, 1, 0) returning id",
                Long.class, online.evaluationId(), registration + "," + rawAnswers);
        jdbcTemplate.update("insert into network_evaluation_answer_record (batch_id, evaluation_student_id, student_registration, raw_answers, valid) values (?, ?, ?, ?, true)", batchId, online.evaluationStudentId(), registration, rawAnswers);
        jdbcTemplate.update("update network_evaluation_student set online_completed_at = current_timestamp, online_token_hash = null where id = ?", online.evaluationStudentId());
        return requireBatch(batchId);
    }

    @Transactional
    public BatchView importCsv(long evaluationId, MultipartFile file, Authentication authentication) {
        accessService.requireNetworkProcess(authentication);
        requireEvaluation(evaluationId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Selecione um arquivo CSV para importar.");
        if (file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("O arquivo CSV deve possuir no máximo 5 MB.");
        String content;
        try { content = new String(file.getBytes(), StandardCharsets.UTF_8); }
        catch (java.io.IOException exception) { throw new IllegalArgumentException("Não foi possível ler o arquivo CSV."); }
        List<RawRecord> rawRecords = parseCsv(content);
        BatchView batch = createBatch(evaluationId, "CSV", safeFilename(file.getOriginalFilename()), content, rawRecords, userId(authentication));
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_ANSWERS_IMPORTED", "NETWORK_EVALUATION", Long.toString(evaluationId), "Gabaritos importados: " + batch.totalRecords() + ", válidos: " + batch.validRecords() + ", inconsistentes: " + batch.invalidRecords() + ".");
        return batch;
    }

    @Transactional
    public BatchView addManualAnswers(long evaluationId, ManualAnswerInput input, Authentication authentication) {
        accessService.requireNetworkProcess(authentication);
        requireEvaluation(evaluationId);
        if (input == null) throw new IllegalArgumentException("Informe a matrícula e as respostas do estudante.");
        List<RawRecord> records = List.of(new RawRecord(clean(input.studentRegistration()), String.join("|", input.answers() == null ? List.of() : input.answers())));
        BatchView batch = createBatch(evaluationId, "MANUAL", null, records.getFirst().registration() + "," + records.getFirst().answers(), records, userId(authentication));
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_ANSWERS_ENTERED", "NETWORK_EVALUATION", Long.toString(evaluationId), "Gabarito inserido manualmente.");
        return batch;
    }

    public List<BatchView> batches(long evaluationId, Authentication authentication) {
        accessService.requireEvaluationRead(authentication, evaluationId);
        return jdbcTemplate.query(
                "select b.*, coalesce(u.display_name, 'Avaliação online') created_by_name from network_evaluation_answer_batch b left join app_user u on u.id = b.created_by where b.evaluation_id = ? order by b.created_at desc, b.id desc",
                this::mapBatch, evaluationId);
    }

    public List<InconsistencyView> inconsistencies(long batchId, Authentication authentication) {
        BatchView batch = requireBatch(batchId);
        accessService.requireEvaluationRead(authentication, batch.evaluationId());
        return jdbcTemplate.query(
                "select student_registration, inconsistency_reason from network_evaluation_answer_record where batch_id = ? and valid = false order by student_registration",
                (rs, rowNum) -> new InconsistencyView(rs.getString("student_registration"), rs.getString("inconsistency_reason")), batchId);
    }

    @Transactional
    public ProcessingRunView process(long batchId, Authentication authentication) {
        accessService.requireNetworkProcess(authentication);
        BatchView batch = requireBatch(batchId);
        EvaluationView evaluation = requireEvaluation(batch.evaluationId());
        if (batch.invalidRecords() > 0) throw new IllegalArgumentException("Corrija as inconsistências antes de processar os resultados.");
        if (batch.totalRecords() == 0) throw new IllegalArgumentException("O lote não possui gabaritos para processar.");
        List<ItemView> itemViews = items(evaluation.id());
        if (itemViews.isEmpty()) throw new IllegalArgumentException("Configure as questões e o gabarito oficial antes do processamento.");
        Integer nextRun = jdbcTemplate.queryForObject("select coalesce(max(run_number), 0) + 1 from network_evaluation_processing_run where evaluation_id = ?", Integer.class, evaluation.id());
        Long previousRun = jdbcTemplate.query("select id from network_evaluation_processing_run where evaluation_id = ? order by run_number desc limit 1", (rs, rowNum) -> rs.getLong("id"), evaluation.id()).stream().findFirst().orElse(null);
        Long runId = jdbcTemplate.queryForObject("insert into network_evaluation_processing_run (evaluation_id, batch_id, run_number, previous_run_id, processed_by) values (?, ?, ?, ?, ?) returning id",
                Long.class, evaluation.id(), batchId, nextRun, previousRun, userId(authentication));
        List<AnswerRecordCore> records = jdbcTemplate.query("select evaluation_student_id, raw_answers from network_evaluation_answer_record where batch_id = ? and valid = true order by id",
                (rs, rowNum) -> new AnswerRecordCore(rs.getLong("evaluation_student_id"), rs.getString("raw_answers")), batchId);
        for (AnswerRecordCore record : records) processRecord(runId, record, itemViews);
        jdbcTemplate.update("update network_evaluation_answer_batch set state = 'PROCESSED' where id = ?", batchId);
        auditService.record(authentication.getName(), "NETWORK_EVALUATION_RESULTS_PROCESSED", "NETWORK_EVALUATION", Long.toString(evaluation.id()), "Processamento #" + nextRun + " criado a partir do lote " + batchId + ".");
        return requireRun(runId);
    }

    public ResultsView results(long evaluationId, Long schoolId, Long classId, Long studentId, Long componentId, String skillCode, String descriptorCode, Authentication authentication) {
        accessService.requireEvaluationRead(authentication, evaluationId);
        if (schoolId != null) accessService.requireResultRead(authentication, schoolId);
        else accessService.requireResultRead(authentication, null);
        Long runId = latestRunId(evaluationId);
        if (runId == null) return new ResultsView(evaluationId, null, 0, 0, 0, null, null, List.of(), List.of());
        StringBuilder where = new StringBuilder(" where r.run_id = ?");
        List<Object> args = new ArrayList<>(); args.add(runId);
        if (schoolId != null) { where.append(" and es.school_id = ?"); args.add(schoolId); }
        if (classId != null) { where.append(" and es.class_id = ?"); args.add(classId); }
        if (studentId != null) { where.append(" and es.student_id = ?"); args.add(studentId); }
        Aggregate aggregate = jdbcTemplate.queryForObject(
                "select count(distinct es.id) participants, coalesce(sum(r.score), 0) score_sum, coalesce(sum(r.max_score), 0) max_sum from network_evaluation_student_result r join network_evaluation_student es on es.id = r.evaluation_student_id" + where,
                (rs, rowNum) -> new Aggregate(rs.getLong("participants"), rs.getBigDecimal("score_sum"), rs.getBigDecimal("max_sum")), args.toArray());
        int organized = countOrganizedForScope(evaluationId, schoolId, classId, studentId);
        List<SkillResultView> skills = skillResults(runId, schoolId, classId, studentId, componentId, skillCode, descriptorCode);
        List<ItemResultView> items = itemResults(runId, schoolId, classId, studentId, componentId);
        long participants = aggregate == null ? 0 : aggregate.participants();
        BigDecimal achievement = aggregate == null ? null : percentage(aggregate.scoreSum(), aggregate.maxSum());
        BigDecimal participation = percentage(BigDecimal.valueOf(participants), BigDecimal.valueOf(organized));
        return new ResultsView(evaluationId, runId, organized, participants, items.size(), participation, achievement, skills, items);
    }

    public List<StageComparisonView> compareStages(int academicYear, String gradeStage, Authentication authentication) {
        accessService.requireResultRead(authentication, null);
        List<EvaluationView> evaluations = jdbcTemplate.query(baseEvaluationSelect() + " where e.academic_year = ? and e.grade_stage = ? order by case e.evaluation_stage when 'DIAGNOSTIC' then 1 when 'MONITORING' then 2 else 3 end, e.id",
                this::mapEvaluation, academicYear, clean(gradeStage));
        List<StageComparisonView> result = new ArrayList<>();
        for (EvaluationView evaluation : evaluations) {
            ResultsView metrics = results(evaluation.id(), null, null, null, null, null, null, authentication);
            result.add(new StageComparisonView(evaluation.id(), evaluation.stage(), evaluation.stageLabel(), evaluation.name(), metrics.participants(), metrics.participationPercent(), metrics.achievementPercent()));
        }
        return result;
    }

    private BatchView createBatch(long evaluationId, String sourceType, String filename, String rawSource, List<RawRecord> rawRecords, Long actorId) {
        List<ItemView> itemViews = items(evaluationId);
        if (itemViews.isEmpty()) throw new IllegalArgumentException("Configure as questões e o gabarito oficial antes de inserir respostas.");
        int valid = 0;
        List<ValidatedRecord> validated = new ArrayList<>();
        for (RawRecord raw : rawRecords) {
            ValidatedRecord next = validateRecord(evaluationId, raw, itemViews.size());
            validated.add(next);
            if (next.valid()) valid++;
        }
        int invalid = rawRecords.size() - valid;
        String state = invalid > 0 ? "WITH_INCONSISTENCIES" : "NOT_PROCESSED";
        Long batchId = jdbcTemplate.queryForObject(
                "insert into network_evaluation_answer_batch (evaluation_id, source_type, original_filename, raw_source, state, total_records, valid_records, invalid_records, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, evaluationId, sourceType, filename, rawSource, state, rawRecords.size(), valid, invalid, actorId);
        for (ValidatedRecord record : validated) {
            jdbcTemplate.update("insert into network_evaluation_answer_record (batch_id, evaluation_student_id, student_registration, raw_answers, valid, inconsistency_reason) values (?, ?, ?, ?, ?, ?)",
                    batchId, record.evaluationStudentId(), record.registration(), record.answers(), record.valid(), record.reason());
        }
        return requireBatch(batchId);
    }

    private ValidatedRecord validateRecord(long evaluationId, RawRecord raw, int expectedItems) {
        String registration = raw.registration() == null ? "" : raw.registration().trim();
        if (registration.isBlank()) return new ValidatedRecord(null, "(sem matrícula)", normalizeRawAnswers(raw.answers()), false, "Matrícula do estudante não informada.");
        List<Long> candidates = jdbcTemplate.query(
                "select es.id from network_evaluation_student es join student s on s.id = es.student_id where es.evaluation_id = ? and s.registration = ?",
                (rs, rowNum) -> rs.getLong("id"), evaluationId, registration);
        if (candidates.isEmpty()) return new ValidatedRecord(null, registration, normalizeRawAnswers(raw.answers()), false, "A matrícula não pertence aos estudantes organizados nesta avaliação.");
        List<String> answers = splitAnswers(raw.answers());
        if (answers.size() != expectedItems) return new ValidatedRecord(candidates.getFirst(), registration, String.join("|", answers), false, "Quantidade de respostas divergente do gabarito: esperado " + expectedItems + ", recebido " + answers.size() + ".");
        if (answers.stream().anyMatch(String::isBlank)) return new ValidatedRecord(candidates.getFirst(), registration, String.join("|", answers), false, "Existem respostas vazias no gabarito.");
        return new ValidatedRecord(candidates.getFirst(), registration, String.join("|", answers), true, null);
    }

    private void processRecord(long runId, AnswerRecordCore record, List<ItemView> itemViews) {
        List<String> answers = splitAnswers(record.rawAnswers());
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        int correct = 0;
        for (int index = 0; index < itemViews.size(); index++) {
            ItemView item = itemViews.get(index);
            String answer = answers.get(index).toUpperCase(Locale.ROOT);
            boolean isCorrect = answer.equals(item.correctAnswer().toUpperCase(Locale.ROOT));
            BigDecimal itemScore = isCorrect ? item.maxScore() : BigDecimal.ZERO;
            if (isCorrect) correct++;
            score = score.add(itemScore); max = max.add(item.maxScore());
            jdbcTemplate.update("insert into network_evaluation_item_result (run_id, evaluation_student_id, item_id, marked_answer, correct, score) values (?, ?, ?, ?, ?, ?)",
                    runId, record.evaluationStudentId(), item.id(), answer, isCorrect, itemScore);
        }
        jdbcTemplate.update("insert into network_evaluation_student_result (run_id, evaluation_student_id, answered_items, correct_items, score, max_score, percentage) values (?, ?, ?, ?, ?, ?, ?)",
                runId, record.evaluationStudentId(), answers.size(), correct, score, max, percentage(score, max));
    }

    private List<SkillResultView> skillResults(long runId, Long schoolId, Long classId, Long studentId, Long componentId, String skillCode, String descriptorCode) {
        StringBuilder sql = new StringBuilder(
                "select i.skill_code, i.skill_label, i.descriptor_code, i.descriptor_label, cc.id component_id, cc.name component_name, count(ir.id) responses, count(ir.id) filter (where ir.correct) correct, coalesce(sum(ir.score),0) score_sum, coalesce(sum(i.max_score),0) max_sum "
                        + "from network_evaluation_item_result ir join network_evaluation_item i on i.id = ir.item_id join curricular_component cc on cc.id = i.component_id join network_evaluation_student es on es.id = ir.evaluation_student_id where ir.run_id = ?");
        List<Object> args = new ArrayList<>(); args.add(runId);
        if (schoolId != null) { sql.append(" and es.school_id = ?"); args.add(schoolId); }
        if (classId != null) { sql.append(" and es.class_id = ?"); args.add(classId); }
        if (studentId != null) { sql.append(" and es.student_id = ?"); args.add(studentId); }
        if (componentId != null) { sql.append(" and i.component_id = ?"); args.add(componentId); }
        if (skillCode != null && !skillCode.isBlank()) { sql.append(" and i.skill_code = ?"); args.add(skillCode.trim()); }
        if (descriptorCode != null && !descriptorCode.isBlank()) { sql.append(" and i.descriptor_code = ?"); args.add(descriptorCode.trim()); }
        sql.append(" group by i.skill_code, i.skill_label, i.descriptor_code, i.descriptor_label, cc.id, cc.name order by cc.name, i.skill_code nulls last, i.descriptor_code nulls last");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new SkillResultView(
                rs.getLong("component_id"), rs.getString("component_name"), rs.getString("skill_code"), rs.getString("skill_label"), rs.getString("descriptor_code"), rs.getString("descriptor_label"),
                rs.getLong("responses"), rs.getLong("correct"), percentage(rs.getBigDecimal("score_sum"), rs.getBigDecimal("max_sum"))), args.toArray());
    }

    private List<ItemResultView> itemResults(long runId, Long schoolId, Long classId, Long studentId, Long componentId) {
        StringBuilder sql = new StringBuilder(
                "select i.id item_id, i.item_number, cc.name component_name, count(ir.id) responses, count(ir.id) filter (where ir.correct) correct, "
                        + "count(ir.id) filter (where ir.marked_answer = 'A') answer_a, count(ir.id) filter (where ir.marked_answer = 'B') answer_b, count(ir.id) filter (where ir.marked_answer = 'C') answer_c, count(ir.id) filter (where ir.marked_answer = 'D') answer_d, count(ir.id) filter (where ir.marked_answer = 'E') answer_e "
                        + "from network_evaluation_item_result ir join network_evaluation_item i on i.id = ir.item_id join curricular_component cc on cc.id = i.component_id join network_evaluation_student es on es.id = ir.evaluation_student_id where ir.run_id = ?");
        List<Object> args = new ArrayList<>(); args.add(runId);
        if (schoolId != null) { sql.append(" and es.school_id = ?"); args.add(schoolId); }
        if (classId != null) { sql.append(" and es.class_id = ?"); args.add(classId); }
        if (studentId != null) { sql.append(" and es.student_id = ?"); args.add(studentId); }
        if (componentId != null) { sql.append(" and i.component_id = ?"); args.add(componentId); }
        sql.append(" group by i.id, i.item_number, cc.name order by i.item_number");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            long responses = rs.getLong("responses");
            Map<String, BigDecimal> distribution = new LinkedHashMap<>();
            for (String letter : List.of("A", "B", "C", "D", "E")) {
                distribution.put(letter, percentage(BigDecimal.valueOf(rs.getLong("answer_" + letter.toLowerCase(Locale.ROOT)))), BigDecimal.valueOf(responses)));
            }
            return new ItemResultView(rs.getLong("item_id"), rs.getInt("item_number"), rs.getString("component_name"), responses, rs.getLong("correct"), percentage(BigDecimal.valueOf(rs.getLong("correct")), BigDecimal.valueOf(responses)), distribution);
        }, args.toArray());
    }

    private EvaluationView requireEvaluation(long evaluationId) {
        List<EvaluationView> rows = jdbcTemplate.query(baseEvaluationSelect() + " where e.id = ?", this::mapEvaluation, evaluationId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Avaliação em Rede não encontrada.");
        return rows.getFirst();
    }

    private String baseEvaluationSelect() {
        return "select e.*, u.display_name created_by_name, "
                + "(select count(*) from network_evaluation_class ec where ec.evaluation_id = e.id) class_count, "
                + "(select count(*) from network_evaluation_student es where es.evaluation_id = e.id) student_count, "
                + "(select count(*) from network_evaluation_item i where i.evaluation_id = e.id) item_count, "
                + "(select max(run_number) from network_evaluation_processing_run pr where pr.evaluation_id = e.id) latest_run "
                + "from network_evaluation e join app_user u on u.id = e.created_by";
    }

    private EvaluationView mapEvaluation(ResultSet rs, int rowNum) throws SQLException {
        String stage = rs.getString("evaluation_stage");
        Integer latestRun = (Integer) rs.getObject("latest_run");
        LocalDate received = toLocalDate(rs.getDate("materials_received_at"));
        return new EvaluationView(rs.getLong("id"), rs.getString("name"), stage, stageLabel(stage), rs.getInt("academic_year"), rs.getString("grade_stage"), rs.getString("status"),
                toLocalDate(rs.getDate("application_date")), received, received == null ? null : addBusinessDays(received, 7), rs.getString("applicator_instructions"), rs.getString("created_by_name"),
                rs.getInt("class_count"), rs.getInt("student_count"), rs.getInt("item_count"), latestRun);
    }

    private List<ItemView> items(long evaluationId) {
        return jdbcTemplate.query(
                "select i.*, cc.code component_code, cc.name component_name from network_evaluation_item i join curricular_component cc on cc.id = i.component_id where i.evaluation_id = ? order by i.item_number",
                (rs, rowNum) -> new ItemView(rs.getLong("id"), rs.getInt("item_number"), rs.getLong("component_id"), rs.getString("component_code"), rs.getString("component_name"), rs.getString("prompt_text"), rs.getString("correct_answer"), rs.getBigDecimal("max_score"), rs.getString("skill_code"), rs.getString("skill_label"), rs.getString("descriptor_code"), rs.getString("descriptor_label")), evaluationId);
    }

    private ItemView requireItem(Long itemId) {
        if (itemId == null) throw new IllegalStateException("Não foi possível salvar a questão.");
        List<ItemView> rows = jdbcTemplate.query(
                "select i.*, cc.code component_code, cc.name component_name from network_evaluation_item i join curricular_component cc on cc.id = i.component_id where i.id = ?",
                (rs, rowNum) -> new ItemView(rs.getLong("id"), rs.getInt("item_number"), rs.getLong("component_id"), rs.getString("component_code"), rs.getString("component_name"), rs.getString("prompt_text"), rs.getString("correct_answer"), rs.getBigDecimal("max_score"), rs.getString("skill_code"), rs.getString("skill_label"), rs.getString("descriptor_code"), rs.getString("descriptor_label")), itemId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Questão não encontrada.");
        return rows.getFirst();
    }

    private BatchView requireBatch(Long batchId) {
        if (batchId == null) throw new IllegalStateException("Não foi possível salvar o lote de gabaritos.");
        List<BatchView> rows = jdbcTemplate.query(
                "select b.*, coalesce(u.display_name, 'Avaliação online') created_by_name from network_evaluation_answer_batch b left join app_user u on u.id = b.created_by where b.id = ?",
                this::mapBatch, batchId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Lote de gabaritos não encontrado.");
        return rows.getFirst();
    }

    private BatchView mapBatch(ResultSet rs, int rowNum) throws SQLException {
        return new BatchView(rs.getLong("id"), rs.getLong("evaluation_id"), rs.getString("source_type"), rs.getString("original_filename"), rs.getString("state"), rs.getInt("total_records"), rs.getInt("valid_records"), rs.getInt("invalid_records"), rs.getString("created_by_name"), rs.getObject("created_at", OffsetDateTime.class));
    }

    private ProcessingRunView requireRun(Long runId) {
        if (runId == null) throw new IllegalStateException("Não foi possível criar o processamento.");
        List<ProcessingRunView> rows = jdbcTemplate.query(
                "select r.id, r.evaluation_id, r.batch_id, r.run_number, r.previous_run_id, u.display_name processed_by_name, r.processed_at from network_evaluation_processing_run r join app_user u on u.id = r.processed_by where r.id = ?",
                (rs, rowNum) -> new ProcessingRunView(rs.getLong("id"), rs.getLong("evaluation_id"), rs.getLong("batch_id"), rs.getInt("run_number"), (Long) rs.getObject("previous_run_id"), rs.getString("processed_by_name"), rs.getObject("processed_at", OffsetDateTime.class)), runId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Processamento não encontrado.");
        return rows.getFirst();
    }

    private StudentView requireStudent(long evaluationId, long evaluationStudentId) {
        List<StudentView> rows = jdbcTemplate.query(
                "select es.id, es.student_id, es.school_id, es.class_id, s.registration, s.name student_name, su.name school_name, c.name class_name, es.organization_status, es.online_token_expires_at, es.online_completed_at from network_evaluation_student es join student s on s.id = es.student_id join school_unit su on su.id = es.school_id join school_class c on c.id = es.class_id where es.evaluation_id = ? and es.id = ?",
                this::mapStudent, evaluationId, evaluationStudentId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Estudante não está organizado nesta avaliação.");
        return rows.getFirst();
    }

    private StudentView mapStudent(ResultSet rs, int rowNum) throws SQLException {
        return new StudentView(rs.getLong("id"), rs.getLong("student_id"), rs.getLong("school_id"), rs.getLong("class_id"), rs.getString("registration"), rs.getString("student_name"), rs.getString("school_name"), rs.getString("class_name"), rs.getString("organization_status"), rs.getObject("online_token_expires_at", OffsetDateTime.class), rs.getObject("online_completed_at", OffsetDateTime.class));
    }

    private ClassSnapshot requireEvaluationClass(long evaluationId, long classId) {
        List<ClassSnapshot> rows = jdbcTemplate.query(
                "select c.id, c.school_id, c.name class_name, s.name school_name from network_evaluation_class ec join school_class c on c.id = ec.class_id join school_unit s on s.id = c.school_id where ec.evaluation_id = ? and c.id = ?",
                (rs, rowNum) -> new ClassSnapshot(rs.getLong("id"), rs.getLong("school_id"), rs.getString("class_name"), rs.getString("school_name")), evaluationId, classId);
        if (rows.isEmpty()) throw new IllegalArgumentException("A turma não pertence a esta avaliação.");
        return rows.getFirst();
    }

    private OccurrenceView requireOccurrence(Long id) {
        if (id == null) throw new IllegalStateException("Não foi possível registrar a ocorrência.");
        List<OccurrenceView> rows = jdbcTemplate.query(
                "select o.id, o.occurred_at, o.description, u.display_name created_by_name from network_evaluation_occurrence o join app_user u on u.id = o.created_by where o.id = ?",
                (rs, rowNum) -> new OccurrenceView(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("description"), rs.getString("created_by_name")), id);
        return rows.getFirst();
    }

    private void replaceClasses(long evaluationId, List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) throw new IllegalArgumentException("Selecione pelo menos uma turma para a avaliação.");
        jdbcTemplate.update("delete from network_evaluation_class where evaluation_id = ?", evaluationId);
        EvaluationCore core = jdbcTemplate.queryForObject("select academic_year, grade_stage from network_evaluation where id = ?", (rs, rowNum) -> new EvaluationCore(rs.getInt("academic_year"), rs.getString("grade_stage")), evaluationId);
        for (Long classId : classIds.stream().distinct().toList()) {
            Integer count = jdbcTemplate.queryForObject("select count(*) from school_class where id = ? and academic_year = ? and stage = ? and active = true", Integer.class, classId, core.academicYear(), core.gradeStage());
            if (count == null || count == 0) throw new IllegalArgumentException("Uma das turmas não corresponde ao ano letivo e à etapa/série informados.");
            jdbcTemplate.update("insert into network_evaluation_class (evaluation_id, class_id) values (?, ?)", evaluationId, classId);
        }
    }

    private boolean sameConfiguredClasses(long evaluationId, List<Long> classIds) {
        List<Long> current = jdbcTemplate.query("select class_id from network_evaluation_class where evaluation_id = ? order by class_id", (rs, rowNum) -> rs.getLong("class_id"), evaluationId);
        List<Long> next = classIds == null ? List.of() : classIds.stream().distinct().sorted().toList();
        return current.equals(next);
    }

    private void validateEvaluationInput(EvaluationInput input) {
        if (input == null) throw new IllegalArgumentException("Informe os dados da avaliação.");
        clean(input.name()); normalizeStage(input.stage()); clean(input.gradeStage()); normalizeStatus(input.status());
        if (input.academicYear() == null || input.academicYear() < 2000 || input.academicYear() > 2200) throw new IllegalArgumentException("Informe um ano letivo válido.");
        if (input.classIds() == null || input.classIds().isEmpty()) throw new IllegalArgumentException("Selecione pelo menos uma turma para a avaliação.");
    }

    private void requireComponent(Long componentId) {
        if (componentId == null) throw new IllegalArgumentException("Selecione o componente curricular da questão.");
        Integer count = jdbcTemplate.queryForObject("select count(*) from curricular_component where id = ? and active = true", Integer.class, componentId);
        if (count == null || count == 0) throw new IllegalArgumentException("Componente curricular não encontrado ou inativo.");
    }

    private List<RawRecord> parseCsv(String content) {
        List<RawRecord> records = new ArrayList<>();
        String[] lines = content.replace("\r", "").split("\n");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isBlank()) continue;
            if (index == 0 && line.toLowerCase(Locale.ROOT).contains("matricula")) continue;
            int separator = line.indexOf(',');
            if (separator < 0) separator = line.indexOf(';');
            if (separator < 0) { records.add(new RawRecord("", line)); continue; }
            records.add(new RawRecord(line.substring(0, separator).replace("\"", "").trim(), line.substring(separator + 1).replace("\"", "").trim()));
        }
        if (records.isEmpty()) throw new IllegalArgumentException("O CSV não possui registros de gabarito. Use as colunas matrícula e respostas.");
        return records;
    }

    private List<String> splitAnswers(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String normalized = raw.trim().replace(';', '|').replace(',', '|');
        if (normalized.contains("|")) return java.util.Arrays.stream(normalized.split("\\|", -1)).map(String::trim).map(value -> value.toUpperCase(Locale.ROOT)).toList();
        List<String> characters = new ArrayList<>();
        for (char character : normalized.toCharArray()) if (!Character.isWhitespace(character)) characters.add(String.valueOf(character).toUpperCase(Locale.ROOT));
        return characters;
    }

    private String normalizeAnswers(List<String> answers, int expected) {
        if (answers == null || answers.size() != expected) throw new IllegalArgumentException("A quantidade de respostas é diferente da quantidade de questões.");
        List<String> normalized = answers.stream().map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT)).toList();
        if (normalized.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Responda todas as questões antes de enviar.");
        return String.join("|", normalized);
    }

    private String normalizeRawAnswers(String answers) { return String.join("|", splitAnswers(answers)); }
    private String registrationForEvaluationStudent(long id) { return jdbcTemplate.queryForObject("select s.registration from network_evaluation_student es join student s on s.id = es.student_id where es.id = ?", String.class, id); }
    private int countStudents(long evaluationId) { return jdbcTemplate.queryForObject("select count(*) from network_evaluation_student where evaluation_id = ?", Integer.class, evaluationId); }
    private int countClasses(long evaluationId) { return jdbcTemplate.queryForObject("select count(*) from network_evaluation_class where evaluation_id = ?", Integer.class, evaluationId); }

    private int countOrganizedForScope(long evaluationId, Long schoolId, Long classId, Long studentId) {
        StringBuilder sql = new StringBuilder("select count(*) from network_evaluation_student where evaluation_id = ?");
        List<Object> args = new ArrayList<>(); args.add(evaluationId);
        if (schoolId != null) { sql.append(" and school_id = ?"); args.add(schoolId); }
        if (classId != null) { sql.append(" and class_id = ?"); args.add(classId); }
        if (studentId != null) { sql.append(" and student_id = ?"); args.add(studentId); }
        return jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
    }

    private Long latestRunId(long evaluationId) {
        return jdbcTemplate.query("select id from network_evaluation_processing_run where evaluation_id = ? order by run_number desc limit 1", (rs, rowNum) -> rs.getLong("id"), evaluationId).stream().findFirst().orElse(null);
    }

    private String normalizeStage(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!STAGES.contains(normalized)) throw new IllegalArgumentException("Selecione a etapa Diagnóstica, Monitoramento ou Final.");
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = value == null || value.isBlank() ? "PREPARATION" : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("Situação da avaliação inválida.");
        return normalized;
    }

    private String stageLabel(String stage) { return stage.equals("DIAGNOSTIC") ? "Diagnóstica" : stage.equals("MONITORING") ? "Monitoramento" : "Final"; }
    private String clean(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Preencha todos os campos obrigatórios."); return value.trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Date nullableDate(LocalDate value) { return value == null ? null : Date.valueOf(value); }
    private LocalDate toLocalDate(Date value) { return value == null ? null : value.toLocalDate(); }
    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) { if (denominator == null || denominator.signum() == 0) return null; return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP); }

    private LocalDate addBusinessDays(LocalDate start, int days) {
        LocalDate date = start; int added = 0;
        while (added < days) { date = date.plusDays(1); if (date.getDayOfWeek().getValue() <= 5) added++; }
        return date;
    }

    private long userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof KrinoUserPrincipal principal)) throw new AccessDeniedException("Usuário autenticado não identificado.");
        return principal.id();
    }

    private String safeFilename(String original) { if (original == null || original.isBlank()) return "gabaritos.csv"; String value = java.nio.file.Paths.get(original).getFileName().toString().replaceAll("[\\p{Cntrl}]", "").trim(); return value.substring(0, Math.min(value.length(), 255)); }
    private String requireToken(String token) { if (token == null || token.isBlank() || token.length() > 200) throw new IllegalArgumentException("O acesso informado é inválido."); return token.trim(); }
    private String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 não disponível.", exception); } }

    public record EvaluationInput(String name, String stage, Integer academicYear, String gradeStage, String status, LocalDate applicationDate, LocalDate materialsReceivedAt, String applicatorInstructions, List<Long> classIds) {}
    public record ItemInput(Integer itemNumber, Long componentId, String promptText, String correctAnswer, BigDecimal maxScore, String skillCode, String skillLabel, String descriptorCode, String descriptorLabel) {}
    public record OccurrenceInput(OffsetDateTime occurredAt, String description) {}
    public record OnlineTokenInput(OffsetDateTime expiresAt) {}
    public record OnlineSubmission(List<String> answers) {}
    public record ManualAnswerInput(String studentRegistration, List<String> answers) {}
    public record EvaluationContext(List<SchoolOption> schools, List<ComponentOption> components) {}
    public record SchoolOption(long id, String code, String name) {}
    public record ClassOption(long id, String name, String stage, String shift) {}
    public record ComponentOption(long id, String code, String name) {}
    public record EvaluationView(long id, String name, String stage, String stageLabel, int academicYear, String gradeStage, String status, LocalDate applicationDate, LocalDate materialsReceivedAt, LocalDate contractualResultsDueDate, String applicatorInstructions, String createdByName, int classCount, int studentCount, int itemCount, Integer latestRun) {}
    public record ItemView(long id, int itemNumber, long componentId, String componentCode, String componentName, String promptText, String correctAnswer, BigDecimal maxScore, String skillCode, String skillLabel, String descriptorCode, String descriptorLabel) {}
    public record OrganizationSummary(int totalStudents, int newlyOrganizedStudents, int classCount) {}
    public record StudentView(long evaluationStudentId, long studentId, long schoolId, long classId, String registration, String name, String schoolName, String className, String organizationStatus, OffsetDateTime onlineTokenExpiresAt, OffsetDateTime onlineCompletedAt) {}
    public record AttendanceRow(int order, String registration, String studentName, String status) {}
    public record MaterialsView(EvaluationView evaluation, String schoolName, String className, List<AttendanceRow> attendance, String packageLabel, String applicatorManual, List<OccurrenceView> occurrences) {}
    public record OccurrenceView(long id, OffsetDateTime occurredAt, String description, String createdByName) {}
    public record OnlineTokenView(String token, OffsetDateTime expiresAt, String studentName, String evaluationName) {}
    public record OnlineItemView(long id, int itemNumber, String componentName, String promptText) {}
    public record OnlineEvaluationView(long evaluationId, long evaluationStudentId, String evaluationName, String studentName, OffsetDateTime expiresAt, List<OnlineItemView> items) {}
    public record BatchView(long id, long evaluationId, String sourceType, String originalFilename, String state, int totalRecords, int validRecords, int invalidRecords, String createdByName, OffsetDateTime createdAt) {}
    public record InconsistencyView(String studentRegistration, String reason) {}
    public record ProcessingRunView(long id, long evaluationId, long batchId, int runNumber, Long previousRunId, String processedByName, OffsetDateTime processedAt) {}
    public record SkillResultView(long componentId, String componentName, String skillCode, String skillLabel, String descriptorCode, String descriptorLabel, long responses, long correctAnswers, BigDecimal achievementPercent) {}
    public record ItemResultView(long itemId, int itemNumber, String componentName, long responses, long correctAnswers, BigDecimal correctPercent, Map<String, BigDecimal> answerDistributionPercent) {}
    public record ResultsView(long evaluationId, Long runId, int organizedStudents, long participants, int itemGroups, BigDecimal participationPercent, BigDecimal achievementPercent, List<SkillResultView> skills, List<ItemResultView> items) {}
    public record StageComparisonView(long evaluationId, String stage, String stageLabel, String evaluationName, long participants, BigDecimal participationPercent, BigDecimal achievementPercent) {}
    private record RawRecord(String registration, String answers) {}
    private record ValidatedRecord(Long evaluationStudentId, String registration, String answers, boolean valid, String reason) {}
    private record AnswerRecordCore(long evaluationStudentId, String rawAnswers) {}
    private record Aggregate(long participants, BigDecimal scoreSum, BigDecimal maxSum) {}
    private record EvaluationCore(int academicYear, String gradeStage) {}
    private record ClassSnapshot(long id, long schoolId, String className, String schoolName) {}
    private record OnlineStudentCore(long evaluationStudentId, long evaluationId, String evaluationName, String status, String studentName, OffsetDateTime expiresAt, OffsetDateTime completedAt) {}
}
