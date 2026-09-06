package br.com.krino.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class FamilyAttendanceCalculatorTest {

    @Test
    void calculaFrequenciaComDuasCasas() {
        assertEquals(new BigDecimal("96.00"), FamilyAttendanceCalculator.percentage(50, 2));
        assertEquals(new BigDecimal("66.67"), FamilyAttendanceCalculator.percentage(3, 1));
    }

    @Test
    void retornaSemBaseQuandoNaoHaAulas() {
        assertNull(FamilyAttendanceCalculator.percentage(0, 0));
    }

    @Test
    void naoRetornaPercentualNegativoQuandoFaltasSuperamAulas() {
        assertEquals(new BigDecimal("0.00"), FamilyAttendanceCalculator.percentage(10, 12));
    }
}
