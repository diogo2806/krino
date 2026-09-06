package br.com.krino.secretaria;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SchoolDocumentService {

    private final JdbcTemplate jdbcTemplate;
    private final SchoolAccessService accessService;

    public SchoolDocumentService(JdbcTemplate jdbcTemplate, SchoolAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public DocumentView generate(String rawType, Long schoolId, Integer year, Long classId, Long studentId, Integer period, Authentication authentication) {
        accessService.requireDocumentRead(authentication, schoolId);
        DocumentType type;
        try { type = DocumentType.valueOf(rawType.toUpperCase()); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Tipo de documento escolar inválido."); }

        return switch (type) {
            case ENROLLMENT_BOOK -> enrollmentBook(schoolId, requireYear(year));
            case ATTENDANCE_DECLARATION -> declaration(type, schoolId, requireStudent(studentId), year);
            case ENROLLMENT_DECLARATION -> declaration(type, schoolId, requireStudent(studentId), year);
            case BOLSA_FAMILIA_DECLARATION -> declaration(type, schoolId, requireStudent(studentId), year);
            case GUARDIAN_PROFESSION_DECLARATION -> declaration(type, schoolId, requireStudent(studentId), year);
            case PROVISIONAL_TRANSFER -> declaration(type, schoolId, requireStudent(studentId), year);
            case INDIVIDUAL_RECORD -> individualRecord(type, schoolId, requireStudent(studentId), requireYear(year));
            case FINAL_RESULT_MINUTES -> classResults(type, schoolId, requireClass(classId), requireYear(year), null);
            case SCHOOL_TRANSCRIPT -> individualRecord(type, schoolId, requireStudent(studentId), requireYear(year));
            case CLASS_STUDENT_LIST -> classStudents(type, schoolId, requireClass(classId));
            case BLANK_ATTENDANCE_LIST -> classStudents(type, schoolId, requireClass(classId));
            case BLANK_GRADE_SHEET -> classStudents(type, schoolId, requireClass(classId));
            case BIMESTRAL_COUNCIL_LIST -> classResults(type, schoolId, requireClass(classId), requireYear(year), requirePeriod(period));
        };
    }

    private DocumentView enrollmentBook(Long schoolId, int year) {
        List<List<String>> rows = jdbcTemplate.query(
                "select st.registration, st.name, c.name, e.enrollment_date, e.enrollment_type, e.status from student_enrollment e join student st on st.id = e.student_id join school_class c on c.id = e.class_id where c.school_id = ? and e.academic_year = ? order by e.enrollment_date, st.name",
                (rs, rowNum) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate().toString(), humanEnrollmentType(rs.getString(5)), humanStatus(rs.getString(6))), schoolId, year);
        return document(DocumentType.ENROLLMENT_BOOK, schoolName(schoolId) + " · " + year, List.of("Matrícula", "Estudante", "Turma", "Data", "Tipo", "Situação"), rows, List.of());
    }

    private DocumentView declaration(DocumentType type, Long schoolId, long studentId, Integer year) {
        StudentEnrollment data = studentEnrollment(schoolId, studentId, year);
        List<String> paragraphs = new ArrayList<>();
        String base = "Declaramos que " + data.studentName() + ", matrícula " + data.registration() + ", encontra-se vinculado(a) à turma " + data.className() + " da unidade " + data.schoolName() + ".";
        switch (type) {
            case ATTENDANCE_DECLARATION -> paragraphs.add(base + " A frequência registrada deve ser consultada nos resultados acadêmicos disponíveis para o período.");
            case ENROLLMENT_DECLARATION -> paragraphs.add(base);
            case BOLSA_FAMILIA_DECLARATION -> paragraphs.add(base + " Documento emitido para fins de comprovação escolar junto ao Programa Bolsa Família.");
            case GUARDIAN_PROFESSION_DECLARATION -> {
                if (data.guardianName() == null || data.guardianProfession() == null) throw new IllegalArgumentException("Não há nome e profissão de responsável suficientes para emitir esta declaração.");
                paragraphs.add(base + " Responsável informado: " + data.guardianName() + ", profissão: " + data.guardianProfession() + ".");
            }
            case PROVISIONAL_TRANSFER -> paragraphs.add(base + " Esta via registra a situação escolar persistida e serve como transferência provisória até a conclusão dos procedimentos administrativos aplicáveis.");
            default -> throw new IllegalArgumentException("Documento incompatível com declaração individual.");
        }
        return document(type, data.schoolName(), List.of(), List.of(), paragraphs);
    }

    private DocumentView individualRecord(DocumentType type, Long schoolId, long studentId, int year) {
        StudentEnrollment data = studentEnrollment(schoolId, studentId, year);
        List<List<String>> rows = jdbcTemplate.query(
                "select cc.name, r.period, r.grade, r.absences, r.classes_count from student_term_result r join curricular_component cc on cc.id = r.component_id join student_enrollment e on e.id = r.enrollment_id where e.student_id = ? and e.academic_year = ? order by cc.name, r.period",
                (rs, rowNum) -> List.of(rs.getString(1), Integer.toString(rs.getInt(2)), rs.getBigDecimal(3) == null ? "Sem lançamento" : rs.getBigDecimal(3).toPlainString(), Integer.toString(rs.getInt(4)), Integer.toString(rs.getInt(5))), studentId, year);
        List<String> paragraphs = List.of(data.studentName() + " · matrícula " + data.registration() + " · turma " + data.className());
        return document(type, data.schoolName() + " · " + year, List.of("Componente", "Período", "Nota", "Faltas", "Aulas"), rows, paragraphs);
    }

    private DocumentView classStudents(DocumentType type, Long schoolId, long classId) {
        verifyClassSchool(classId, schoolId);
        List<String> headers = switch (type) {
            case BLANK_ATTENDANCE_LIST -> List.of("Matrícula", "Estudante", "Presença/assinatura");
            case BLANK_GRADE_SHEET -> List.of("Matrícula", "Estudante", "Nota");
            default -> List.of("Matrícula", "Estudante", "Situação");
        };
        List<List<String>> rows = jdbcTemplate.query(
                "select st.registration, st.name, e.status from student_enrollment e join student st on st.id = e.student_id where e.class_id = ? order by st.name",
                (rs, rowNum) -> type == DocumentType.CLASS_STUDENT_LIST
                        ? List.of(rs.getString(1), rs.getString(2), humanStatus(rs.getString(3)))
                        : List.of(rs.getString(1), rs.getString(2), ""), classId);
        return document(type, className(classId), headers, rows, List.of(schoolName(schoolId)));
    }

    private DocumentView classResults(DocumentType type, Long schoolId, long classId, int year, Integer period) {
        verifyClassSchool(classId, schoolId);
        String sql = "select st.registration, st.name, cc.name, r.period, r.grade, r.absences from student_enrollment e join student st on st.id = e.student_id left join student_term_result r on r.enrollment_id = e.id left join curricular_component cc on cc.id = r.component_id where e.class_id = ? and e.academic_year = ?" + (period == null ? "" : " and (r.period = ? or r.period is null)") + " order by st.name, cc.name";
        List<List<String>> rows = period == null
                ? jdbcTemplate.query(sql, (rs, rowNum) -> resultRow(rs), classId, year)
                : jdbcTemplate.query(sql, (rs, rowNum) -> resultRow(rs), classId, year, period);
        return document(type, className(classId) + " · " + year, List.of("Matrícula", "Estudante", "Componente", "Período", "Nota", "Faltas"), rows, List.of(schoolName(schoolId)));
    }

    private List<String> resultRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Object period = rs.getObject(4); Object grade = rs.getObject(5); Object absences = rs.getObject(6);
        return List.of(rs.getString(1), rs.getString(2), rs.getString(3) == null ? "Sem lançamento" : rs.getString(3), period == null ? "-" : period.toString(), grade == null ? "Sem lançamento" : grade.toString(), absences == null ? "0" : absences.toString());
    }

    private StudentEnrollment studentEnrollment(Long schoolId, long studentId, Integer year) {
        String yearFilter = year == null ? "" : " and e.academic_year = ?";
        String sql = "select st.registration, st.name, st.guardian_name, st.guardian_profession, s.name school_name, c.name class_name, e.academic_year from student st join student_enrollment e on e.student_id = st.id join school_class c on c.id = e.class_id join school_unit s on s.id = c.school_id where st.id = ? and c.school_id = ?" + yearFilter + " order by e.academic_year desc, e.id desc limit 1";
        List<StudentEnrollment> rows = year == null
                ? jdbcTemplate.query(sql, (rs, rowNum) -> mapStudentEnrollment(rs), studentId, schoolId)
                : jdbcTemplate.query(sql, (rs, rowNum) -> mapStudentEnrollment(rs), studentId, schoolId, year);
        if (rows.isEmpty()) throw new IllegalArgumentException("Não foi encontrada matrícula do estudante nesta unidade/ano para emitir o documento.");
        return rows.getFirst();
    }

    private StudentEnrollment mapStudentEnrollment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StudentEnrollment(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7));
    }

    private void verifyClassSchool(long classId, long schoolId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from school_class where id = ? and school_id = ?", Integer.class, classId, schoolId);
        if (count == null || count == 0) throw new IllegalArgumentException("A turma selecionada não pertence à unidade escolar informada.");
    }

    private String schoolName(long id) { String value = jdbcTemplate.queryForObject("select name from school_unit where id = ?", String.class, id); if (value == null) throw new IllegalArgumentException("Unidade escolar não encontrada."); return value; }
    private String className(long id) { String value = jdbcTemplate.queryForObject("select name from school_class where id = ?", String.class, id); if (value == null) throw new IllegalArgumentException("Turma não encontrada."); return value; }
    private int requireYear(Integer value) { if (value == null) throw new IllegalArgumentException("Informe o ano letivo para emitir este documento."); return value; }
    private int requirePeriod(Integer value) { if (value == null || value < 1 || value > 4) throw new IllegalArgumentException("Informe o bimestre entre 1 e 4."); return value; }
    private long requireStudent(Long value) { if (value == null) throw new IllegalArgumentException("Selecione o estudante para emitir este documento."); return value; }
    private long requireClass(Long value) { if (value == null) throw new IllegalArgumentException("Selecione a turma para emitir este documento."); return value; }

    private DocumentView document(DocumentType type, String subtitle, List<String> headers, List<List<String>> rows, List<String> paragraphs) {
        return new DocumentView(type.name(), type.title, subtitle, headers, rows, paragraphs, Instant.now());
    }

    private String humanEnrollmentType(String value) { return switch (value) { case "REENROLLMENT" -> "Rematrícula"; case "CLASS_CHANGE" -> "Troca de turma"; default -> "Matrícula"; }; }
    private String humanStatus(String value) { return switch (value) { case "TRANSFERRED" -> "Transferido"; case "CLASS_CHANGED" -> "Troca de turma"; case "DECEASED" -> "Falecimento"; case "COMPLETED" -> "Concluído"; default -> "Ativo"; }; }

    public enum DocumentType {
        ENROLLMENT_BOOK("Livro de matrícula"), ATTENDANCE_DECLARATION("Declaração de frequência"), ENROLLMENT_DECLARATION("Declaração de matrícula"), BOLSA_FAMILIA_DECLARATION("Declaração para Bolsa Família"), GUARDIAN_PROFESSION_DECLARATION("Declaração com profissão do responsável"), PROVISIONAL_TRANSFER("Transferência provisória"), INDIVIDUAL_RECORD("Ficha individual"), FINAL_RESULT_MINUTES("Ata de resultado final"), SCHOOL_TRANSCRIPT("Histórico escolar"), CLASS_STUDENT_LIST("Lista de estudantes por turma"), BLANK_ATTENDANCE_LIST("Ata/lista de presença em branco"), BLANK_GRADE_SHEET("Planilha de notas em branco"), BIMESTRAL_COUNCIL_LIST("Lista para conselho escolar bimestral");
        private final String title; DocumentType(String title) { this.title = title; }
    }

    public record DocumentView(String type, String title, String subtitle, List<String> headers, List<List<String>> rows, List<String> paragraphs, Instant generatedAt) {}
    private record StudentEnrollment(String registration, String studentName, String guardianName, String guardianProfession, String schoolName, String className, int academicYear) {}
}
