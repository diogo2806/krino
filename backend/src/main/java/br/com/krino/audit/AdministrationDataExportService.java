package br.com.krino.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdministrationDataExportService {

    private final JdbcTemplate jdbcTemplate;

    public AdministrationDataExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdministrationExport export() {
        List<String> tableNames = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE' and table_name <> 'flyway_schema_history' order by table_name",
                String.class);

        List<TableExport> tables = new ArrayList<>();
        Map<String, List<String>> omittedSensitiveColumns = new LinkedHashMap<>();

        for (String tableName : tableNames) {
            List<String> columns = jdbcTemplate.queryForList(
                    "select column_name from information_schema.columns where table_schema = 'public' and table_name = ? order by ordinal_position",
                    String.class,
                    tableName);
            List<String> omitted = columns.stream().filter(this::isSensitiveColumn).toList();
            List<String> exportable = columns.stream().filter(column -> !isSensitiveColumn(column)).toList();

            if (!omitted.isEmpty()) {
                omittedSensitiveColumns.put(tableName, omitted);
            }

            List<Map<String, Object>> rows;
            if (exportable.isEmpty()) {
                rows = List.of();
            } else {
                String selectColumns = exportable.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
                String sql = "select " + selectColumns + " from " + quoteIdentifier(tableName);
                rows = jdbcTemplate.queryForList(sql);
            }
            tables.add(new TableExport(tableName, exportable, rows));
        }

        return new AdministrationExport(
                "krino-administration-export-v1",
                OffsetDateTime.now(ZoneOffset.UTC),
                "JSON",
                omittedSensitiveColumns,
                tables);
    }

    private boolean isSensitiveColumn(String columnName) {
        String normalized = columnName.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("credential");
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public record AdministrationExport(
            String formatVersion,
            OffsetDateTime generatedAt,
            String format,
            Map<String, List<String>> omittedSensitiveColumns,
            List<TableExport> tables) {
    }

    public record TableExport(String tableName, List<String> columns, List<Map<String, Object>> rows) {
    }
}
