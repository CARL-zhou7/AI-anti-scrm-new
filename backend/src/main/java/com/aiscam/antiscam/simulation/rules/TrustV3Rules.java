package com.aiscam.antiscam.simulation.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure trust-v3 scoring (D5/D8). One event list is processed strictly in order:
 * a decisive counterevidence activates its subject and blocks every later positive event; a resolution removes
 * only its own subject and never scores by itself; positive events resume only after the last active subject is
 * released. Counterevidence targets are computed from the frozen initial score, never from the running score.
 */
public final class TrustV3Rules {
    public static final int CONFIRMED_OFFSET = 20;
    private TrustV3Rules() {}

    public record V3Event(TrustEventType type, String subjectKey, String progressKey, CounterevidenceLevel counterevidenceLevel) {
        public V3Event(TrustEventType type, String subjectKey, String progressKey) {
            this(type, subjectKey, progressKey, null);
        }
    }

    public record ScoredEvent(TrustEventType type, String subjectKey, String progressKey, int baseDelta, BigDecimal factor,
            int delta, int scoreBefore, int scoreAfter, CounterevidenceLevel counterevidenceLevel, Integer targetScore,
            boolean constraintActive) {}

    /** @param highestLevels every subject ever accepted, including already released ones (never deleted). */
    public record ScoreResult(List<ScoredEvent> events, Set<String> activeSubjects,
            Map<String, CounterevidenceLevel> highestLevels) {}

    public static int nominal(int base, BigDecimal factor) {
        return BigDecimal.valueOf(base).multiply(factor).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    public static ScoreResult score(int initialScore, int currentScore, BigDecimal positiveFactor, BigDecimal negativeFactor,
            List<V3Event> events, Set<String> activeSubjects, Map<String, CounterevidenceLevel> highestLevels) {
        if (initialScore < 0 || initialScore > 100 || currentScore < 0 || currentScore > 100
                || !validFactor(positiveFactor) || !validFactor(negativeFactor)) {
            throw new IllegalArgumentException("Invalid trust parameters");
        }
        var active = new LinkedHashSet<>(activeSubjects == null ? Set.of() : activeSubjects);
        var highest = new LinkedHashMap<>(highestLevels == null ? Map.of() : highestLevels);
        var scored = new ArrayList<ScoredEvent>();
        int before = currentScore;
        for (V3Event event : events) {
            var type = event.type();
            if (type == TrustEventType.DECISIVE_COUNTEREVIDENCE) {
                var level = event.counterevidenceLevel() == null ? CounterevidenceLevel.PARTIAL : event.counterevidenceLevel();
                var previous = highest.get(event.subjectKey());
                if (!level.strongerThan(previous)) continue;
                highest.put(event.subjectKey(), level);
                active.add(event.subjectKey());
                int target = level == CounterevidenceLevel.PARTIAL && previous == null
                        ? initialScore : Math.max(0, initialScore - CONFIRMED_OFFSET);
                int delta = Math.min(0, target - before);
                int after = clamp(before + delta);
                scored.add(new ScoredEvent(type, event.subjectKey(), event.progressKey(), type.baseDeltaV3(), BigDecimal.ONE,
                        after - before, before, after, level, target, false));
                before = after;
                continue;
            }
            if (type == TrustEventType.COUNTEREVIDENCE_RESOLVED) {
                if (!active.remove(event.subjectKey())) continue;
                scored.add(new ScoredEvent(type, event.subjectKey(), event.progressKey(), type.baseDeltaV3(), BigDecimal.ONE,
                        0, before, before, null, null, false));
                continue;
            }
            int base = type.baseDeltaV3();
            if (type.positiveInV3() && !active.isEmpty()) {
                scored.add(new ScoredEvent(type, event.subjectKey(), event.progressKey(), base, positiveFactor,
                        0, before, before, null, null, true));
                continue;
            }
            BigDecimal factor = base > 0 ? positiveFactor : base < 0 ? negativeFactor : BigDecimal.ONE;
            int after = clamp(before + nominal(base, factor));
            scored.add(new ScoredEvent(type, event.subjectKey(), event.progressKey(), base, factor, after - before,
                    before, after, null, null, false));
            before = after;
        }
        return new ScoreResult(List.copyOf(scored), Set.copyOf(active), Map.copyOf(highest));
    }

    private static int clamp(int score) { return Math.max(0, Math.min(100, score)); }

    private static boolean validFactor(BigDecimal factor) {
        return factor != null && factor.compareTo(new BigDecimal("0.10")) >= 0 && factor.compareTo(new BigDecimal("3.00")) <= 0;
    }
}
