package br.com.krino.report;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CsvWriter {

    public String write(List<List<?>> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        for (List<?> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) csv.append(',');
                csv.append(escape(row.get(index)));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    String escape(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
