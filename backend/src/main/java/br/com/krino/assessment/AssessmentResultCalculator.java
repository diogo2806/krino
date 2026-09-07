package br.com.krino.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class AssessmentResultCalculator {

    public Result calculate(List<AnswerEvaluation> answers) {
        int total = answers.size();
        int correct = (int) answers.stream().filter(AnswerEvaluation::correct).count();
        Map<String, MutableSkillResult> grouped = new LinkedHashMap<>();
        for (AnswerEvaluation answer : answers) {
            String key = answer.descriptor() + "\u0000" + answer.skill();
            MutableSkillResult item = grouped.computeIfAbsent(key, ignored -> new MutableSkillResult(answer.descriptor(), answer.skill()));
            item.total++;
            if (answer.correct()) item.correct++;
        }
        List<SkillResult> skills = new ArrayList<>();
        for (MutableSkillResult item : grouped.values()) {
            skills.add(new SkillResult(item.descriptor, item.skill, item.correct, item.total, percentage(item.correct, item.total)));
        }
        return new Result(correct, total, percentage(correct, total), skills);
    }

    private BigDecimal percentage(int numerator, int denominator) {
        if (denominator == 0) return null;
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    public record AnswerEvaluation(String descriptor, String skill, boolean correct) {}
    public record SkillResult(String descriptor, String skill, int correctAnswers, int totalQuestions, BigDecimal scorePercent) {}
    public record Result(int correctAnswers, int totalQuestions, BigDecimal scorePercent, List<SkillResult> skills) {}

    private static final class MutableSkillResult {
        private final String descriptor;
        private final String skill;
        private int correct;
        private int total;

        private MutableSkillResult(String descriptor, String skill) {
            this.descriptor = descriptor;
            this.skill = skill;
        }
    }
}
