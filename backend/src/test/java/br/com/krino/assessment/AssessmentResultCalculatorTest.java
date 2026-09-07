package br.com.krino.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.krino.assessment.AssessmentResultCalculator.AnswerEvaluation;

class AssessmentResultCalculatorTest {

    private final AssessmentResultCalculator calculator = new AssessmentResultCalculator();

    @Test
    void shouldCalculateKnownSeventyPercentScenario() {
        List<AnswerEvaluation> answers = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            answers.add(new AnswerEvaluation(index <= 5 ? "D1" : "D2", index <= 5 ? "Habilidade 1" : "Habilidade 2", index <= 7));
        }

        AssessmentResultCalculator.Result result = calculator.calculate(answers);

        assertThat(result.totalQuestions()).isEqualTo(10);
        assertThat(result.correctAnswers()).isEqualTo(7);
        assertThat(result.scorePercent()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(result.skills()).hasSize(2);
        assertThat(result.skills().get(0).scorePercent()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.skills().get(1).scorePercent()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void shouldReturnNullPercentWhenThereAreNoQuestions() {
        AssessmentResultCalculator.Result result = calculator.calculate(List.of());

        assertThat(result.totalQuestions()).isZero();
        assertThat(result.correctAnswers()).isZero();
        assertThat(result.scorePercent()).isNull();
        assertThat(result.skills()).isEmpty();
    }
}
