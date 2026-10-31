package com.aiscam.antiscam.simulation.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class TrustRules {
    private TrustRules() {}

    public record RawEvent(TrustEventType type, String subjectKey) {}
    public record ScoredEvent(TrustEventType type, String subjectKey, int baseDelta, BigDecimal factor,
            int delta, int scoreBefore, int scoreAfter) {}

    public static int nominal(int base, BigDecimal factor) {
        return BigDecimal.valueOf(base).multiply(factor).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    public static List<ScoredEvent> score(int initialScore, BigDecimal positiveFactor, BigDecimal negativeFactor,
            List<RawEvent> events) {
        if (initialScore < 0 || initialScore > 100 || !validFactor(positiveFactor) || !validFactor(negativeFactor)) {
            throw new IllegalArgumentException("Invalid trust parameters");
        }
        var result = new ArrayList<ScoredEvent>();
        int before = initialScore;
        for (var event : events) {
            int base = event.type().baseDelta();
            BigDecimal factor = base > 0 ? positiveFactor : negativeFactor;
            int after = Math.max(0, Math.min(100, before + nominal(base, factor)));
            result.add(new ScoredEvent(event.type(), event.subjectKey(), base, factor, after - before, before, after));
            before = after;
        }
        return List.copyOf(result);
    }

    private static boolean validFactor(BigDecimal factor) {
        return factor != null && factor.compareTo(new BigDecimal("0.10")) >= 0 && factor.compareTo(new BigDecimal("3.00")) <= 0;
    }
}
