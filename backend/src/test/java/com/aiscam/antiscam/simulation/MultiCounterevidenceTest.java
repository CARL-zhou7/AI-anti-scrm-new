package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.simulation.domain.SimulationMessageRepository;
import com.aiscam.antiscam.simulation.domain.SimulationRoundRepository;
import com.aiscam.antiscam.simulation.domain.SimulationSessionRepository;
import com.aiscam.antiscam.simulation.domain.SimulationTrustEvent;
import com.aiscam.antiscam.simulation.domain.SimulationTrustEventRepository;
import com.aiscam.antiscam.simulation.domain.FundFlowRepository;
import com.aiscam.antiscam.simulation.rules.CounterevidenceLevel;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.rules.TrustV3Rules;
import com.aiscam.antiscam.simulation.rules.TrustV3Rules.V3Event;
import com.aiscam.antiscam.simulation.service.TrustEventService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * D8: two independent counterevidence subjects, one PARTIAL→CONFIRMED upgrade, and the rule that a repeat at the
 * same level, a downgrade or a retry after release never deducts again or reactivates a subject.
 */
class MultiCounterevidenceTest {
    private static final BigDecimal ONE = BigDecimal.ONE;

    private java.util.List<SimulationTrustEvent> saved = java.util.List.of();
    private final SimulationTrustEventRepository events = fake(SimulationTrustEventRepository.class,
            (name, args) -> name.equals("findBySessionIdOrderByIdAsc") ? saved : null);
    private final TrustEventService service = new TrustEventService(fake(SimulationSessionRepository.class, (n, a) -> null),
            fake(SimulationRoundRepository.class, (n, a) -> null), fake(SimulationMessageRepository.class, (n, a) -> null),
            events, fake(FundFlowRepository.class, (n, a) -> null), null);

    /** Dependency-free interface stub: only the method under test is implemented. */
    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "fake-" + type.getSimpleName();
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> handler.apply(method.getName(), args);
                });
    }

    private SimulationTrustEvent event(long id, TrustEventType type, String subject, String progress,
            CounterevidenceLevel level, Integer target, int before, int after) {
        var scored = new TrustV3Rules.ScoredEvent(type, subject, progress, type.baseDeltaV3(), ONE, after - before,
                before, after, level, target, false);
        var saved = new SimulationTrustEvent(7, 1, null, scored, "SYSTEM", "原文");
        try {
            var field = SimulationTrustEvent.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(saved, id);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return saved;
    }

    @Test
    void stateIsRebuiltFromHistoryAndSurvivesRelease() {
        saved = List.of(
                event(1, TrustEventType.DECISIVE_COUNTEREVIDENCE, "A", "A首次", CounterevidenceLevel.PARTIAL, 50, 75, 50),
                event(2, TrustEventType.DECISIVE_COUNTEREVIDENCE, "A", "A升级", CounterevidenceLevel.CONFIRMED, 30, 50, 30),
                event(3, TrustEventType.COUNTEREVIDENCE_RESOLVED, "A", "A已解释", null, null, 30, 30),
                event(4, TrustEventType.DECISIVE_COUNTEREVIDENCE, "B", "B首次", CounterevidenceLevel.PARTIAL, 50, 30, 30));
        var state = service.counterevidenceState(7L);
        assertEquals(Set.of("B"), state.activeSubjects());
        assertEquals(Map.of("A", CounterevidenceLevel.CONFIRMED, "B", CounterevidenceLevel.PARTIAL), state.highestLevels());

        var reloaded = service.counterevidenceState(7L);
        assertEquals(state, reloaded, "reloading and retrying must produce the same state");
    }

    @Test
    void repeatDowngradeAndReleasedReSubmissionNeverDeductAgain() {
        var highest = Map.of("A", CounterevidenceLevel.CONFIRMED);

        var sameLevel = TrustV3Rules.score(50, 30, ONE, ONE,
                List.of(new V3Event(TrustEventType.DECISIVE_COUNTEREVIDENCE, "A", "换一个进展键", CounterevidenceLevel.CONFIRMED)),
                Set.of(), highest);
        assertTrue(sameLevel.events().isEmpty(), "same level with a new progress key must not deduct");
        assertTrue(sameLevel.activeSubjects().isEmpty(), "a released subject must not be reactivated at the same level");

        var downgrade = TrustV3Rules.score(50, 30, ONE, ONE,
                List.of(new V3Event(TrustEventType.DECISIVE_COUNTEREVIDENCE, "A", "降档", CounterevidenceLevel.PARTIAL)),
                Set.of(), highest);
        assertTrue(downgrade.events().isEmpty());

        var upgrade = TrustV3Rules.score(50, 70, ONE, ONE,
                List.of(new V3Event(TrustEventType.DECISIVE_COUNTEREVIDENCE, "A", "新证据", CounterevidenceLevel.CONFIRMED)),
                Set.of(), Map.of("A", CounterevidenceLevel.PARTIAL));
        assertEquals(-40, upgrade.events().getFirst().delta());
        assertEquals(30, upgrade.events().getFirst().scoreAfter());
        assertTrue(upgrade.activeSubjects().contains("A"), "an upgrade keeps the subject active");
    }

    @Test
    void releasingOneSubjectNeverReleasesTheOtherAndPositivesStayBlocked() {
        var result = TrustV3Rules.score(50, 30, ONE, ONE, List.of(
                new V3Event(TrustEventType.COUNTEREVIDENCE_RESOLVED, "A", "A已解释"),
                new V3Event(TrustEventType.NEW_EFFECTIVE_EXPLANATION, "解释", "费用说明")),
                Set.of("A", "B"), Map.of("A", CounterevidenceLevel.CONFIRMED, "B", CounterevidenceLevel.PARTIAL));
        assertEquals(Set.of("B"), result.activeSubjects());
        assertEquals(0, result.events().getLast().delta());
        assertTrue(result.events().getLast().constraintActive());

        var unknown = TrustV3Rules.score(50, 30, ONE, ONE,
                List.of(new V3Event(TrustEventType.COUNTEREVIDENCE_RESOLVED, "C", "未知主题")),
                Set.of("A", "B"), Map.of("A", CounterevidenceLevel.CONFIRMED, "B", CounterevidenceLevel.PARTIAL));
        assertTrue(unknown.events().isEmpty());
        assertEquals(Set.of("A", "B"), unknown.activeSubjects());
    }
}
