package br.com.krino.monitoring;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.audit.SecurityAuditService;
import br.com.krino.monitoring.PedagogicalMetricProvider.MetricFilter;
import br.com.krino.monitoring.PedagogicalMetricProvider.SourceMetric;

@Service
public class PedagogicalMonitoringService {

    private final JdbcTemplate jdbcTemplate;
    private final MonitoringAccessService accessService;
    private final List<PedagogicalMetricProvider> metricProviders;
    private final SecurityAuditService auditService;

    public PedagogicalMonitoringService(JdbcTemplate jdbcTemplate, MonitoringAccessService accessService,
                                        List<PedagogicalMetricProvider> metricProviders, SecurityAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
        this.metricProviders = metricProviders;
        this.auditService = auditService;
    }

    public MonitoringContext context(int academicYear, Authentication authentication) {
        List<Long> ids = accessService.accessibleSchoolIds(authentication);
        List<SchoolOption> schools = ids.isEmpty() ? List.of() : jdbcTemplate.query(
                "select id, code, name from school_unit where id = any(?) order by name",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray())),
                (rs, rowNum) -> new SchoolOption(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
        return new MonitoringContext(academicYear, accessService.canNetworkRead(authentication), accessService.canNetworkManage(authentication), schools);
    }

    public List<ClassOption> classes(long schoolId, int academicYear, Authentication authentication) {
        accessService.requireRead(schoolId, authentication);
        return jdbcTemplate.query(
                "select id, name, stage from school_class where school_id = ? and academic_year = ? and active = true order by name",
                (rs, rowNum) -> new ClassOption(rs.getLong("id"), rs.getString("name"), rs.getString("stage")), schoolId, academicYear);
    }

    public List<StudentOption> students(long classId, int academicYear, Authentication authentication) {
        Scope scope = validateScope(academicYear, null, classId, null, authentication);
        return jdbcTemplate.query(
                "select distinct st.id, st.registration, st.name from student_enrollment e join student st on st.id = e.student_id "
                        + "where e.class_id = ? and e.academic_year = ? order by st.name",
                (rs, rowNum) -> new StudentOption(rs.getLong("id"), rs.getString("registration"), rs.getString("name")),
                scope.classId(), academicYear);
    }

    public MonitoringSummary summary(int academicYear, Integer period, Long schoolId, Long classId, Long studentId,
                                     Authentication authentication) {
        validatePeriod(period);
        Scope scope = validateScope(academicYear, schoolId, classId, studentId, authentication);
        MetricFilter filter = new MetricFilter(academicYear, period, scope.schoolId(), scope.classId(), scope.studentId());
        List<SourceMetric> sources = metricProviders.stream().map(provider -> provider.load(filter)).toList();
        return new MonitoringSummary(scope.level(), academicYear, period, scope.schoolId(), scope.schoolName(), scope.classId(), scope.className(),
                scope.studentId(), scope.studentName(), sources);
    }

    public List<TrendPoint> trend(int academicYear, Long schoolId, Long classId, Long studentId, Authentication authentication) {
        validateScope(academicYear, schoolId, classId, studentId, authentication);
        return List.of(1, 2, 3, 4).stream()
                .map(period -> new TrendPoint(period, summary(academicYear, period, schoolId, classId, studentId, authentication).sources()))
                .toList();
    }

    public List<BreakdownItem> breakdown(int academicYear, Integer period, Long schoolId, Long classId, Authentication authentication) {
        validatePeriod(period);
        if (classId != null) {
            Scope scope = validateScope(academicYear, schoolId, classId, null, authentication);
            return jdbcTemplate.query(
                    "select distinct st.id, st.name from student_enrollment e join student st on st.id = e.student_id where e.class_id = ? and e.academic_year = ? order by st.name",
                    (rs, rowNum) -> new BreakdownTarget(rs.getLong("id"), rs.getString("name")), scope.classId(), academicYear)
                    .stream()
                    .map(target -> new BreakdownItem("STUDENT", target.id(), target.label(), summary(academicYear, period, scope.schoolId(), scope.classId(), target.id(), authentication).sources()))
                    .toList();
        }
        if (schoolId != null) {
            accessService.requireRead(schoolId, authentication);
            return jdbcTemplate.query("select id, name from school_class where school_id = ? and academic_year = ? and active = true order by name",
                    (rs, rowNum) -> new BreakdownTarget(rs.getLong("id"), rs.getString("name")), schoolId, academicYear)
                    .stream()
                    .map(target -> new BreakdownItem("CLASS", target.id(), target.label(), summary(academicYear, period, schoolId, target.id(), null, authentication).sources()))
                    .toList();
        }
        accessService.requireNetworkRead(authentication);
        return jdbcTemplate.query("select id, name from school_unit where active = true order by name",
                (rs, rowNum) -> new BreakdownTarget(rs.getLong("id"), rs.getString("name")))
                .stream()
                .map(target -> new BreakdownItem("SCHOOL", target.id(), target.label(), summary(academicYear, period, target.id(), null, null, authentication).sources()))
                .toList();
    }

    public List<IndicatorRecordView> indicatorRecords(int academicYear, Long schoolId, Long classId, Long studentId,
                                                       Authentication authentication) {
        Scope scope = validateScope(academicYear, schoolId, classId, studentId, authentication);
        String baseSql = "select r.id, r.indicator, r.record_type, r.scope_type, r.school_id, s.name school_name, r.class_id, c.name class_name, "
                + "r.student_id, st.name student_name, r.academic_year, r.scenario_name, r.source_reference, r.assumptions, r.indicator_value, "
                + "r.classification, r.created_by, r.created_at from pedagogical_indicator_record r "
                + "left join school_unit s on s.id = r.school_id left join school_class c on c.id = r.class_id left join student st on st.id = r.student_id ";
        return switch (scope.level()) {
            case "NETWORK" -> jdbcTemplate.query(baseSql + "where r.scope_type = 'NETWORK' and r.academic_year = ? order by r.indicator, r.record_type, r.created_at desc",
                    this::mapIndicatorRecord, academicYear);
            case "SCHOOL" -> jdbcTemplate.query(baseSql + "where r.scope_type = 'SCHOOL' and r.school_id = ? and r.academic_year = ? order by r.indicator, r.record_type, r.created_at desc",
                    this::mapIndicatorRecord, scope.schoolId(), academicYear);
            case "CLASS" -> jdbcTemplate.query(baseSql + "where r.scope_type = 'CLASS' and r.class_id = ? and r.academic_year = ? order by r.indicator, r.record_type, r.created_at desc",
                    this::mapIndicatorRecord, scope.classId(), academicYear);
            case "STUDENT" -> jdbcTemplate.query(baseSql + "where r.scope_type = 'STUDENT' and r.class_id = ? and r.student_id = ? and r.academic_year = ? order by r.indicator, r.record_type, r.created_at desc",
                    this::mapIndicatorRecord, scope.classId(), scope.studentId(), academicYear);
            default -> List.of();
        };
    }

    @Transactional
    public IndicatorRecordView createIndicatorRecord(IndicatorRecordRequest request, Authentication authentication) {
        String indicator = normalizeIndicator(request.indicator());
        String recordType = normalizeRecordType(request.recordType());
        Scope scope = validateScope(request.academicYear(), request.schoolId(), request.classId(), request.studentId(), authentication);
        accessService.requireManage(scope.schoolId(), authentication);
        String classification = recordType.equals("OBSERVED_RESULT") ? "DOCUMENTED_REFERENCE" : "NON_OFFICIAL";
        Long id = jdbcTemplate.queryForObject(
                "insert into pedagogical_indicator_record (indicator, record_type, scope_type, school_id, class_id, student_id, academic_year, scenario_name, source_reference, assumptions, indicator_value, classification, created_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, indicator, recordType, scope.level(), scope.schoolId(), scope.classId(), scope.studentId(), request.academicYear(),
                request.scenarioName().trim(), request.sourceReference().trim(), request.assumptions(), request.value(), classification, authentication.getName());
        auditService.record(authentication.getName(), "PEDAGOGICAL_INDICATOR_RECORDED", "PEDAGOGICAL_INDICATOR", Long.toString(id),
                indicator + " / " + recordType + " / " + request.academicYear() + " / " + scope.level());
        return getIndicatorRecord(id);
    }

    private Scope validateScope(int academicYear, Long schoolId, Long classId, Long studentId, Authentication authentication) {
        if (studentId != null) {
            if (classId == null) throw new IllegalArgumentException("Selecione a turma antes de selecionar o estudante.");
            List<StudentScope> rows = jdbcTemplate.query(
                    "select st.id student_id, st.name student_name, c.id class_id, c.name class_name, c.school_id, s.name school_name, c.academic_year "
                            + "from student_enrollment e join student st on st.id = e.student_id join school_class c on c.id = e.class_id join school_unit s on s.id = c.school_id "
                            + "where e.student_id = ? and e.class_id = ? and e.academic_year = ?",
                    (rs, rowNum) -> new StudentScope(rs.getLong("student_id"), rs.getString("student_name"), rs.getLong("class_id"), rs.getString("class_name"),
                            rs.getLong("school_id"), rs.getString("school_name"), rs.getInt("academic_year")),
                    studentId, classId, academicYear);
            if (rows.isEmpty()) throw new IllegalArgumentException("O estudante selecionado não possui matrícula nesta turma e ano letivo.");
            StudentScope selected = rows.getFirst();
            if (schoolId != null && !schoolId.equals(selected.schoolId())) throw new IllegalArgumentException("O estudante selecionado não pertence à unidade escolar informada.");
            accessService.requireRead(selected.schoolId(), authentication);
            return new Scope("STUDENT", selected.schoolId(), selected.schoolName(), selected.classId(), selected.className(), selected.studentId(), selected.studentName());
        }
        if (classId != null) {
            List<ClassScope> rows = jdbcTemplate.query(
                    "select c.id, c.name, c.school_id, s.name school_name, c.academic_year from school_class c join school_unit s on s.id = c.school_id where c.id = ?",
                    (rs, rowNum) -> new ClassScope(rs.getLong("id"), rs.getString("name"), rs.getLong("school_id"), rs.getString("school_name"), rs.getInt("academic_year")), classId);
            if (rows.isEmpty()) throw new IllegalArgumentException("Turma não encontrada.");
            ClassScope selected = rows.getFirst();
            if (selected.academicYear() != academicYear) throw new IllegalArgumentException("A turma selecionada não pertence ao ano letivo informado.");
            if (schoolId != null && !schoolId.equals(selected.schoolId())) throw new IllegalArgumentException("A turma selecionada não pertence à unidade escolar informada.");
            accessService.requireRead(selected.schoolId(), authentication);
            return new Scope("CLASS", selected.schoolId(), selected.schoolName(), selected.id(), selected.name(), null, null);
        }
        if (schoolId != null) {
            accessService.requireRead(schoolId, authentication);
            String schoolName = jdbcTemplate.queryForObject("select name from school_unit where id = ?", String.class, schoolId);
            return new Scope("SCHOOL", schoolId, schoolName, null, null, null, null);
        }
        accessService.requireNetworkRead(authentication);
        return new Scope("NETWORK", null, "Rede municipal", null, null, null, null);
    }

    private void validatePeriod(Integer period) {
        if (period != null && (period < 1 || period > 4)) throw new IllegalArgumentException("O período deve estar entre 1 e 4.");
    }

    private String normalizeIndicator(String indicator) {
        String value = indicator == null ? "" : indicator.trim().toUpperCase();
        if (!(value.equals("IDEB") || value.equals("IDEPE"))) throw new IllegalArgumentException("Indicador deve ser IDEB ou IDEPE.");
        return value;
    }

    private String normalizeRecordType(String recordType) {
        String value = recordType == null ? "" : recordType.trim().toUpperCase();
        if (!(value.equals("OBSERVED_RESULT") || value.equals("SIMULATION") || value.equals("PROJECTION"))) {
            throw new IllegalArgumentException("Tipo deve ser resultado observado, simulação ou projeção.");
        }
        return value;
    }

    private IndicatorRecordView getIndicatorRecord(long id) {
        return jdbcTemplate.queryForObject(
                "select r.id, r.indicator, r.record_type, r.scope_type, r.school_id, s.name school_name, r.class_id, c.name class_name, r.student_id, st.name student_name, "
                        + "r.academic_year, r.scenario_name, r.source_reference, r.assumptions, r.indicator_value, r.classification, r.created_by, r.created_at "
                        + "from pedagogical_indicator_record r left join school_unit s on s.id = r.school_id left join school_class c on c.id = r.class_id left join student st on st.id = r.student_id where r.id = ?",
                this::mapIndicatorRecord, id);
    }

    private IndicatorRecordView mapIndicatorRecord(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new IndicatorRecordView(rs.getLong("id"), rs.getString("indicator"), rs.getString("record_type"), rs.getString("scope_type"),
                nullableLong(rs, "school_id"), rs.getString("school_name"), nullableLong(rs, "class_id"), rs.getString("class_name"),
                nullableLong(rs, "student_id"), rs.getString("student_name"), rs.getInt("academic_year"), rs.getString("scenario_name"),
                rs.getString("source_reference"), rs.getString("assumptions"), rs.getBigDecimal("indicator_value"), rs.getString("classification"),
                rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record Scope(String level, Long schoolId, String schoolName, Long classId, String className, Long studentId, String studentName) {}
    private record ClassScope(Long id, String name, Long schoolId, String schoolName, int academicYear) {}
    private record StudentScope(Long studentId, String studentName, Long classId, String className, Long schoolId, String schoolName, int academicYear) {}
    private record BreakdownTarget(Long id, String label) {}

    public record SchoolOption(Long id, String code, String name) {}
    public record ClassOption(Long id, String name, String stage) {}
    public record StudentOption(Long id, String registration, String name) {}
    public record MonitoringContext(Integer academicYear, boolean networkView, boolean networkManage, List<SchoolOption> schools) {}
    public record MonitoringSummary(String level, Integer academicYear, Integer period, Long schoolId, String schoolName, Long classId, String className,
                                    Long studentId, String studentName, List<SourceMetric> sources) {}
    public record TrendPoint(Integer period, List<SourceMetric> sources) {}
    public record BreakdownItem(String level, Long id, String label, List<SourceMetric> sources) {}
    public record IndicatorRecordRequest(@NotBlank(message = "Selecione o indicador.") String indicator,
                                         @NotBlank(message = "Selecione o tipo de registro.") String recordType,
                                         Long schoolId,
                                         Long classId,
                                         Long studentId,
                                         @NotNull(message = "Informe o ano de referência.") @Min(value = 2000, message = "Informe um ano de referência válido.") Integer academicYear,
                                         @NotBlank(message = "Informe o nome do cenário ou referência.") String scenarioName,
                                         @NotBlank(message = "Informe a origem dos dados utilizados.") String sourceReference,
                                         String assumptions,
                                         @NotNull(message = "Informe o valor do indicador.") @PositiveOrZero(message = "O valor não pode ser negativo.") BigDecimal value) {}
    public record IndicatorRecordView(Long id, String indicator, String recordType, String scopeType, Long schoolId, String schoolName,
                                      Long classId, String className, Long studentId, String studentName, Integer academicYear,
                                      String scenarioName, String sourceReference, String assumptions, BigDecimal value,
                                      String classification, String createdBy, OffsetDateTime createdAt) {}
}
