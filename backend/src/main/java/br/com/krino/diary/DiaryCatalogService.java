package br.com.krino.diary;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class DiaryCatalogService {

    private final JdbcTemplate jdbcTemplate;
    private final DiaryAccessService accessService;

    public DiaryCatalogService(JdbcTemplate jdbcTemplate, DiaryAccessService accessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accessService = accessService;
    }

    public List<SchoolView> schools(Authentication authentication) {
        List<Long> ids = accessService.accessibleSchoolIds(authentication);
        if (ids.isEmpty()) return List.of();
        return jdbcTemplate.query(
                "select id, code, name, address, active from school_unit where id = any(?) order by name",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray())),
                (rs, rowNum) -> new SchoolView(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("address"), rs.getBoolean("active")));
    }

    public List<ClassView> classes(long schoolId, int year, Authentication authentication) {
        accessService.requireSchoolRead(schoolId, authentication);
        return jdbcTemplate.query(
                "select c.id, c.school_id, s.name school_name, c.academic_year, c.name, c.stage, c.shift, c.active "
                        + "from school_class c join school_unit s on s.id = c.school_id "
                        + "where c.school_id = ? and c.academic_year = ? and c.active = true order by c.name",
                (rs, rowNum) -> new ClassView(rs.getLong("id"), rs.getLong("school_id"), rs.getString("school_name"), rs.getInt("academic_year"), rs.getString("name"), rs.getString("stage"), rs.getString("shift"), rs.getBoolean("active")),
                schoolId, year);
    }

    public List<ProfessionalView> professionals(long schoolId, Authentication authentication) {
        accessService.requireSchoolAdmin(schoolId, authentication);
        return jdbcTemplate.query(
                "select id, origin_school_id, registration, name, professional_type, active from education_professional "
                        + "where origin_school_id = ? and active = true order by name",
                (rs, rowNum) -> new ProfessionalView(rs.getLong("id"), nullableLong(rs, "origin_school_id"), rs.getString("registration"), rs.getString("name"), rs.getString("professional_type"), rs.getBoolean("active")),
                schoolId);
    }

    public List<ComponentView> components(Authentication authentication) {
        if (accessService.accessibleSchoolIds(authentication).isEmpty()) {
            throw new AccessDeniedException("Sua conta não possui unidade escolar autorizada para o Diário de Classe.");
        }
        return jdbcTemplate.query("select id, code, name from curricular_component where active = true order by name",
                (rs, rowNum) -> new ComponentView(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record SchoolView(Long id, String code, String name, String address, boolean active) {}
    public record ClassView(Long id, Long schoolId, String schoolName, Integer academicYear, String name, String stage, String shift, boolean active) {}
    public record ProfessionalView(Long id, Long schoolId, String registration, String name, String professionalType, boolean active) {}
    public record ComponentView(Long id, String code, String name) {}
}
