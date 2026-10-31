package com.aiscam.antiscam.simulation.domain;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Draft/adoption rules for the administrator review gate (Phase 2). No Spring, no database: the domain objects and
 * the target schemas are exercised directly.
 */
class InterventionRulesTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private static SimulationScammerDraft draft() {
        return new SimulationScammerDraft(5, 7, 1, 0, "原始台词", "原始动作", "原始台词", "原始动作", null, 9);
    }

    @Test
    void adoptingTheSameDraftTwiceIsIdempotent() {
        var draft = draft();
        draft.adopt();
        assertEquals(ScammerDraftStatus.ADOPTED, draft.getStatus());
        assertThrows(IllegalStateException.class, draft::adopt, "a second adoption must not create another state");
        assertFalse(draft.isActive());
    }

    @Test
    void aDraftFromAnotherRoundCannotBeAdopted() {
        // The service rejects by round; the domain keeps the invariant that only an active DRAFT can be adopted.
        var draft = draft();
        draft.discard();
        assertThrows(IllegalStateException.class, draft::adopt);
        assertEquals(ScammerDraftStatus.DISCARDED, draft.getStatus());
    }

    @Test
    void editingRequiresTheCurrentRevisionAndBumpsIt() {
        var draft = draft();
        assertThrows(IllegalStateException.class, () -> draft.revise("改", null, null, 3));
        draft.revise("新的台词", "新的动作", "请更贴近场景", 0);
        assertEquals(1, draft.getRevisionNo());
        assertEquals("新的台词", draft.getDraftContent());
        assertEquals("原始台词", draft.getOriginalContent(), "the original line is never overwritten");
        assertThrows(IllegalStateException.class, () -> draft.revise("旧修订", null, null, 0));
    }

    @Test
    void interventionCoreRecordsTheAdoptedContentAndLinksOnce() {
        var core = new SimulationIntervention(5, 7, 1, 11, "TASK_REBATE", 9, "原始台词", "原始动作",
                "最终台词", "最终动作", "需要更明确的退路", InterventionMode.ADMIN_TEST);
        assertEquals(InterventionStatus.PENDING, core.getStatus());
        assertNull(core.getFinalMessageId());
        core.link(101);
        assertEquals(InterventionStatus.ADOPTED, core.getStatus(), "adopted and waiting for the knowledge base");
        assertTrue(core.getStatus().awaitingKnowledgeBase());
        assertEquals(101L, core.getFinalMessageId());
        core.link(101);
        assertThrows(IllegalStateException.class, () -> core.link(102), "one intervention links one formal message");
        core.linkToRule();
        assertEquals(InterventionStatus.LINKED, core.getStatus(), "used as a source of a scene rule");
        assertFalse(core.getStatus().awaitingKnowledgeBase());
    }

    @Test
    void phaseSchemaIncludesTheReviewGate() {
        assertEquals(RoundPhase.SCAMMER_REVIEW, RoundPhase.valueOf("SCAMMER_REVIEW"));
        assertEquals(8, RoundPhase.values().length, "one review phase is added, no existing phase is removed");
        assertEquals(InterventionMode.NONE, new SimulationSession(1, "r", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{}", NOW, NOW, "{}", 1000).getInterventionMode(), "ordinary sessions default to NONE");
        var admin = new SimulationSession(1, "r", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4, "{}", NOW, NOW, "{}", 1000);
        admin.setInterventionMode(InterventionMode.ADMIN_TEST);
        assertTrue(admin.isAdminTest());
    }

    @Test
    void draftListOrderingKeepsEveryRevision() {
        var first = draft();
        first.revise("修订一", null, null, 0);
        var second = new SimulationScammerDraft(5, 7, 1, 2, "原始台词", null, "修订二", null, "再次生成", 9);
        assertEquals(List.of(1, 2), List.of(first.getRevisionNo(), second.getRevisionNo()));
        assertEquals(ScammerDraftStatus.DRAFT, second.getStatus());
    }
}
