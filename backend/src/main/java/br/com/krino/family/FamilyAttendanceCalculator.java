package br.com.krino.family;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class FamilyAttendanceCalculator {

    private FamilyAttendanceCalculator() {}

    static BigDecimal percentage(int classesCount, int absences) {
        if (classesCount <= 0) return null;
        int attended = Math.max(0, classesCount - absences);
        return BigDecimal.valueOf(attended).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(classesCount), 2, RoundingMode.HALF_UP);
    }
}
