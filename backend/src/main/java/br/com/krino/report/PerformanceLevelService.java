package br.com.krino.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.krino.assessment.AssessmentAccessService;
import br.com.krino.report.AssessmentReportService.PerformanceLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Service
public class PerformanceLevelService {

    private final JdbcTemplate jdbcTemplate;
    private final AssessmentAccessService assessmentAccessService;

    public PerformanceLevelService(JdbcTemplate jdbcTemplate, AssessmentAccessService assessmentAccessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.assessmentAccessService = assessmentAccessService;
    }

    @Transactional
    public List<PerformanceLevel> replace(long assessmentId, @NotEmpty List<@Valid PerformanceLevelRequest> requests,
            Authentication authentication) {
        assessmentAccessService.requireNetworkWrite(authentication);
        Integer assessment = jdbcTemplate.queryForObject("select count(*) from network_assessment where id = ?", Integer.class, assessmentId);
        if (assessment == null || assessment == 0) throw new IllegalArgumentException("Avaliação em Rede não encontrada.");
        validate(requests);
        jdbcTemplate.update("delete from network_assessment_performance_level where assessment_id = ?", assessmentId);
        int order = 1;
        for (PerformanceLevelRequest request : requests) {
            jdbcTemplate.update(
                    "insert into network_assessment_performance_level (assessment_id, label, minimum_percent, maximum_percent, display_order, created_by) values (?, ?, ?, ?, ?, ?)",
                    assessmentId, request.label().trim(), request.minimumPercent(), request.maximumPercent(), order++, authentication.getName());
        }
        return list(assessmentId);
    }

    public List<PerformanceLevel> list(long assessmentId) {
        return jdbcTemplate.query(
                "select id, label, minimum_percent, maximum_percent, display_order from network_assessment_performance_level where assessment_id = ? order by display_order",
                (rs, rowNum) -> new PerformanceLevel(rs.getLong("id"), rs.getString("label"), rs.getBigDecimal("minimum_percent"),
                        rs.getBigDecimal("maximum_percent"), rs.getInt("display_order")), assessmentId);
    }

    private void validate(List<PerformanceLevelRequest> requests) {
        List<PerformanceLevelRequest> ordered = new ArrayList<>(requests);
        ordered.sort(Comparator.comparing(PerformanceLevelRequest::minimumPercent));
        BigDecimal previousMaximum = null;
        for (PerformanceLevelRequest request : ordered) {
            if (request.minimumPercent().compareTo(request.maximumPercent()) > 0) {
                throw new IllegalArgumentException("A faixa mínima não pode superar a faixa máxima.");
            }
            if (previousMaximum != null && request.minimumPercent().compareTo(previousMaximum) <= 0) {
                throw new IllegalArgumentException("As faixas de desempenho não podem se sobrepor.");
            }
            previousMaximum = request.maximumPercent();
        }
    }

    public record PerformanceLevelRequest(@NotBlank(message = "Informe o nome do nível.") String label,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minimumPercent,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maximumPercent) {}
}
