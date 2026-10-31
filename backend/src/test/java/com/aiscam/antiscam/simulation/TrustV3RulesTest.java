package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.simulation.rules.CounterevidenceLevel;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.rules.TrustV3Rules;
import com.aiscam.antiscam.simulation.rules.TrustV3Rules.V3Event;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.aiscam.antiscam.simulation.rules.TrustEventType.*;
import static org.junit.jupiter.api.Assertions.*;

class TrustV3RulesTest {
    private static final BigDecimal ONE = BigDecimal.ONE;

    private static TrustV3Rules.ScoreResult score(int initial, int current, List<V3Event> events) {
        return TrustV3Rules.score(initial, current, ONE, ONE, events, Set.of(), Map.of());
    }

    private static V3Event event(TrustEventType type, String subject) {
        return new V3Event(type, subject, subject + ":进展");
    }

    @Test
    void twelveBaseValuesAreFixedForV3() {
        assertEquals(3, NEW_EFFECTIVE_EXPLANATION.baseDeltaV3());
        assertEquals(3, PROFESSIONAL_EXPLANATION.baseDeltaV3());
        assertEquals(4, DOUBT_RESOLVED.baseDeltaV3());
        assertEquals(4, RELEVANT_MATERIAL.baseDeltaV3());
        assertEquals(5, PROMISE_FULFILLED.baseDeltaV3());
        assertEquals(2, INDEPENDENT_SUPPORT.baseDeltaV3());
        assertEquals(-3, INDEPENDENT_DOUBT.baseDeltaV3());
        assertEquals(-3, EVASIVE_RESPONSE.baseDeltaV3());
        assertEquals(-6, NEW_CONTRADICTION.baseDeltaV3());
        assertEquals(0, REPEAT_NO_PROGRESS.baseDeltaV3());
        assertEquals(0, DECISIVE_COUNTEREVIDENCE.baseDeltaV3());
        assertEquals(0, COUNTEREVIDENCE_RESOLVED.baseDeltaV3());
        // The legacy values are untouched for PRD-V3.4 sessions.
        assertEquals(-5, NEW_CONTRADICTION.baseDelta());
        assertEquals(-10, DECISIVE_COUNTEREVIDENCE.baseDelta());
        assertEquals(1, INDEPENDENT_SUPPORT.baseDelta());
    }

    @Test
    void counterevidenceTargetsStartFromTheFrozenInitialScore() {
        var partial = score(50, 75, List.of(new V3Event(DECISIVE_COUNTEREVIDENCE, "材料日期矛盾", "日期矛盾",
                CounterevidenceLevel.PARTIAL)));
        assertEquals(50, partial.events().getFirst().targetScore());
        assertEquals(50, partial.events().getFirst().scoreAfter());
        assertEquals(-25, partial.events().getFirst().delta());
        assertTrue(partial.activeSubjects().contains("材料日期矛盾"));

        var confirmed = score(50, 75, List.of(new V3Event(DECISIVE_COUNTEREVIDENCE, "材料日期矛盾", "日期矛盾",
                CounterevidenceLevel.CONFIRMED)));
        assertEquals(30, confirmed.events().getFirst().targetScore());
        assertEquals(30, confirmed.events().getFirst().scoreAfter());

        var belowTarget = TrustV3Rules.score(50, 25, ONE, ONE,
                List.of(new V3Event(DECISIVE_COUNTEREVIDENCE, "材料日期矛盾", "日期矛盾", CounterevidenceLevel.CONFIRMED)),
                Set.of("材料日期矛盾"), Map.of("材料日期矛盾", CounterevidenceLevel.PARTIAL));
        assertEquals(30, belowTarget.events().getFirst().targetScore());
        assertEquals(0, belowTarget.events().getFirst().delta());
        assertEquals(25, belowTarget.events().getFirst().scoreAfter());
    }

    @Test
    void sameOrWeakerLevelNeverDeductsOrReactivates() {
        var upgrade = TrustV3Rules.score(50, 70, ONE, ONE,
                List.of(new V3Event(DECISIVE_COUNTEREVIDENCE, "材料日期矛盾", "新证据", CounterevidenceLevel.CONFIRMED)),
                Set.of(), Map.of("材料日期矛盾", CounterevidenceLevel.PARTIAL));
        assertEquals(1, upgrade.events().size());
        assertEquals(CounterevidenceLevel.CONFIRMED, upgrade.highestLevels().get("材料日期矛盾"));

        var repeat = TrustV3Rules.score(50, 40, ONE, ONE,
                List.of(new V3Event(DECISIVE_COUNTEREVIDENCE, "材料日期矛盾", "另一个进展", CounterevidenceLevel.CONFIRMED)),
                Set.of("材料日期矛盾"), Map.of("材料日期矛盾", CounterevidenceLevel.CONFIRMED));
        assertTrue(repeat.events().isEmpty());

        var downgrade = TrustV3Rules.score(50, 40, ONE, ONE,
                List.of(new V3Event(DECISIVE_COUNTEREVIDENCE, "材料日期矛盾", "回头档位", CounterevidenceLevel.PARTIAL)),
                Set.of("材料日期矛盾"), Map.of("材料日期矛盾", CounterevidenceLevel.CONFIRMED));
        assertTrue(downgrade.events().isEmpty());
        assertEquals(Map.of("材料日期矛盾", CounterevidenceLevel.CONFIRMED), downgrade.highestLevels());
    }

    @Test
    void roundingAndClampingFollowHalfUpAndZeroToHundred() {
        var rounding = TrustV3Rules.score(50, 50, new BigDecimal("1.30"), ONE,
                List.of(event(PROMISE_FULFILLED, "报酬")), Set.of(), Map.of());
        assertEquals(7, rounding.events().getFirst().delta());
        assertEquals(57, rounding.events().getFirst().scoreAfter());

        var capped = TrustV3Rules.score(98, 98, ONE, ONE, List.of(event(RELEVANT_MATERIAL, "材料")), Set.of(), Map.of());
        assertEquals(2, capped.events().getFirst().delta());
        assertEquals(100, capped.events().getFirst().scoreAfter());

        var floored = TrustV3Rules.score(50, 3, ONE, ONE, List.of(event(NEW_CONTRADICTION, "说法冲突")), Set.of(), Map.of());
        assertEquals(0, floored.events().getFirst().scoreAfter());
    }

    @Test
    void releasingOneOfTwoSubjectsKeepsPositiveEventsBlocked() {
        var result = TrustV3Rules.score(50, 50, ONE, ONE, List.of(
                new V3Event(DECISIVE_COUNTEREVIDENCE, "A", "A1", CounterevidenceLevel.PARTIAL),
                new V3Event(DECISIVE_COUNTEREVIDENCE, "B", "B1", CounterevidenceLevel.PARTIAL),
                new V3Event(COUNTEREVIDENCE_RESOLVED, "A", "A已解释"),
                event(NEW_EFFECTIVE_EXPLANATION, "解释")), Set.of(), Map.of());
        // A: 50 -> 50 (target is the initial score and the score already equals it), B: target 50 so no change.
        assertEquals(50, result.events().getLast().scoreBefore());
        assertTrue(result.events().getLast().constraintActive());
        assertEquals(0, result.events().getLast().delta());
        assertEquals(Set.of("B"), result.activeSubjects());

        var released = TrustV3Rules.score(50, 50, ONE, ONE, List.of(
                new V3Event(COUNTEREVIDENCE_RESOLVED, "B", "B已解释"),
                event(NEW_EFFECTIVE_EXPLANATION, "解释")), Set.of("B"), Map.of("B", CounterevidenceLevel.PARTIAL));
        assertEquals(0, released.events().getFirst().delta());
        assertEquals(3, released.events().getLast().delta());
        assertEquals(53, released.events().getLast().scoreAfter());
        assertTrue(released.activeSubjects().isEmpty());
    }

    @Test
    void eventsBeforeAndAfterAResolutionInOneListAreScoredInOrder() {
        var result = TrustV3Rules.score(50, 60, ONE, ONE, List.of(
                event(NEW_EFFECTIVE_EXPLANATION, "解释A"),
                new V3Event(COUNTEREVIDENCE_RESOLVED, "A", "A已解释"),
                event(NEW_EFFECTIVE_EXPLANATION, "解释B")), Set.of("A"), Map.of("A", CounterevidenceLevel.PARTIAL));
        assertEquals(0, result.events().get(0).delta());
        assertTrue(result.events().get(0).constraintActive());
        assertEquals(60, result.events().get(1).scoreAfter());
        assertEquals(3, result.events().get(2).delta());
        assertEquals(63, result.events().get(2).scoreAfter());
    }

    @Test
    void unknownSubjectReleaseChangesNothing() {
        var result = TrustV3Rules.score(50, 50, ONE, ONE,
                List.of(new V3Event(COUNTEREVIDENCE_RESOLVED, "C", "C已解释")), Set.of("A"), Map.of("A", CounterevidenceLevel.PARTIAL));
        assertTrue(result.events().isEmpty());
        assertEquals(Set.of("A"), result.activeSubjects());
    }

    @Test
    void functionCopiesStateInsteadOfMutatingTheInput() {
        var active = new java.util.LinkedHashSet<String>();
        active.add("A");
        var highest = new java.util.LinkedHashMap<String, CounterevidenceLevel>();
        highest.put("A", CounterevidenceLevel.PARTIAL);
        var result = TrustV3Rules.score(50, 50, ONE, ONE,
                List.of(new V3Event(COUNTEREVIDENCE_RESOLVED, "A", "A已解释")), active, highest);
        assertEquals(Set.of("A"), active);
        assertEquals(Map.of("A", CounterevidenceLevel.PARTIAL), highest);
        assertTrue(result.activeSubjects().isEmpty());
        assertEquals(Map.of("A", CounterevidenceLevel.PARTIAL), result.highestLevels());
    }
}
