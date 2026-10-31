package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.*;
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.*;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.State;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Service-level review flow (Phase 2): a delayed "continue" from an earlier round, a stale revision, and a repeated
 * adopt must never advance the victim twice.
 */
class InterventionReviewFlowTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final long ADMIN = 7;

    private final SimulationSessionRepository sessions = mock(SimulationSessionRepository.class);
    private final SimulationRoundRepository rounds = mock(SimulationRoundRepository.class);
    private final SimulationMessageRepository messages = mock(SimulationMessageRepository.class);
    private final SimulationScammerDraftRepository drafts = mock(SimulationScammerDraftRepository.class);
    private final SimulationInterventionRepository interventions = mock(SimulationInterventionRepository.class);
    private final SimulationTransitions transitions = mock(SimulationTransitions.class);
    private final InterventionStateAssembler assembler = mock(InterventionStateAssembler.class);
    private final SimulationStateAssembler stateAssembler = mock(SimulationStateAssembler.class);
    private final DraftContentValidator validator = mock(DraftContentValidator.class);
    private final InterventionService service = new InterventionService(sessions, rounds, messages, drafts,
            interventions, transitions, assembler, stateAssembler, validator);

    private SimulationSession session;
    private SimulationRound round;
    private SimulationScammerDraft draft;

    @BeforeEach
    void setUp() {
        session = new SimulationSession(ADMIN, "req", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{\"ruleset_version\":\"PRD-V3.5\"}", NOW, NOW, "{}", 1000);
        withId(session, 5L);
        session.setInterventionMode(InterventionMode.ADMIN_TEST);
        session.enterRound(3);
        round = withId(new SimulationRound(5, 3, NOW), 9L);
        round.setPhase(RoundPhase.SCAMMER_REVIEW);
        draft = withId(new SimulationScammerDraft(5, 9, 3, 2, "原始台词", null, "草稿台词", null, null, ADMIN), 11L);
        when(sessions.findById(5L)).thenReturn(Optional.of(session));
        when(rounds.findBySessionIdOrderByRoundNoAsc(5L)).thenReturn(List.of(round));
        when(rounds.findBySessionIdAndRoundNo(5L, 3)).thenReturn(Optional.of(round));
        when(stateAssembler.assemble(any())).thenReturn(mock(State.class));
        when(assembler.currentDraft(any())).thenReturn(null);
        when(assembler.assemble(any(), any())).thenReturn(new InterventionState("5", "ADMIN_TEST", 3, "SCAMMER_REVIEW",
                null, 0, true, true));
    }

    private AdoptRequest adopt(String draftId, int revision, String content) {
        return new AdoptRequest(draftId, revision, true, content);
    }

    @Test
    void restoresTheStoredAiOriginalWithoutAskingTheModelToRewriteOrJudgeItAgain() {
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        service.revise(ADMIN, 5, new DraftRequest("11", 2, null, draft.getOriginalContent()));
        verifyNoInteractions(validator);
        verify(transitions).reviseDraft(5, 3, 11, 2, draft.getOriginalContent(), draft.getDraftAction(), null);
    }

    @Test
    void regenerationPassesTheDirectionForBothSpeechAndAction() {
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        service.regenerate(ADMIN, 5, new DraftRequest("11", 2, "  先解释流程，再发送平台备案截图  ", null));
        verify(transitions).discardDraft(5, 3, 11, 2, "先解释流程，再发送平台备案截图");
        verifyNoInteractions(validator);
    }

    @Test
    void regenerationAcceptsAnEmptyDirectionButRejectsAnOversizedOne() {
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        service.regenerate(ADMIN, 5, new DraftRequest("11", 2, null, null));
        verify(transitions).discardDraft(5, 3, 11, 2, "");
        clearInvocations(transitions);
        assertThrows(ApiException.class, () -> service.regenerate(ADMIN, 5,
                new DraftRequest("11", 2, "字".repeat(201), null)));
        verifyNoInteractions(transitions);
    }

    @Test
    void directEditingPreservesTheExistingAction() {
        draft = withId(new SimulationScammerDraft(5, 9, 3, 2, "原始台词", "发送备案截图",
                "草稿台词", "发送备案截图", null, ADMIN), 11L);
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        service.revise(ADMIN, 5, new DraftRequest("11", 2, null, "只修改台词文字"));
        verify(transitions).reviseDraft(5, 3, 11, 2, "只修改台词文字", "发送备案截图", null);
    }

    @Test
    void draftFromAnEarlierRoundIsRejectedAsStale() {
        var other = withId(new SimulationScammerDraft(5, 8, 2, 1, "旧轮台词", null, "旧轮草稿", null, null, ADMIN), 99L);
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        assertThrows(ApiException.class, () -> service.adopt(ADMIN, 5, adopt("99", 1, null)),
                "a draft id that is not the current round's active draft is stale");
        verify(transitions, never()).adoptDraft(anyLong(), anyInt(), anyLong(), anyInt(), any(), any(), any());
        assertNotNull(other); // the old round's draft is never selected by the lookup above
    }

    @Test
    void staleRevisionIsRejectedBeforeAnyContentWork() {
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        var error = assertThrows(ApiException.class, () -> service.adopt(ADMIN, 5, adopt("11", 1, null)));
        assertEquals("DRAFT_REVISION_CONFLICT", error.code);
        verify(validator, never()).validate(any(), any(), any(), any());
        verify(transitions, never()).adoptDraft(anyLong(), anyInt(), anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void adoptingTwiceOnlySavesTheLineOnce() {

        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        service.adopt(ADMIN, 5, adopt("11", 2, null));
        verify(transitions, times(1)).adoptDraft(5, 3, 11, 2, "草稿台词", null, null);

        // The draft is now ADOPTED, so the replay path answers with the existing state instead of adopting again.
        var adopted = withId(new SimulationScammerDraft(5, 9, 3, 2, "原始台词", null, "草稿台词", null, null, ADMIN), 11L);
        adopted.adopt();
        when(drafts.findById(11L)).thenReturn(Optional.of(adopted));
        service.adopt(ADMIN, 5, adopt("11", 2, null));
        verify(transitions, times(1)).adoptDraft(anyLong(), anyInt(), anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void continuingTheSavedDraftDoesNotSendItThroughTheRewriteValidatorAgain() {
        String line = "您好，我是陈启明，刚加您微信的投资顾问。看到您关注科技股，想分享一个稳健的小额体验机会。";
        draft = withId(new SimulationScammerDraft(5, 9, 3, 0, line, null, line, null, null, ADMIN), 11L);
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        doThrow(ApiException.field("content", "CONTENT_REJECTED", "不应把原稿当作人工改写重新审校"))
                .when(validator).validate(any(), any(), any(), any());

        service.adopt(ADMIN, 5, adopt("11", 0, null));

        verifyNoInteractions(validator);
        verify(transitions).adoptDraft(5, 3, 11, 0, line, null, null);
    }

    @Test
    void submittingTheSameSavedContentPreservesItsActionWithoutAnotherReview() {
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        service.adopt(ADMIN, 5, adopt("11", 2, "  草稿台词  "));
        verifyNoInteractions(validator);
        verify(transitions).adoptDraft(5, 3, 11, 2, "草稿台词", null, null);
    }

    @Test
    void replayWithDifferentContentIsAnIdempotencyConflict() {
        var adopted = withId(new SimulationScammerDraft(5, 9, 3, 2, "原始台词", null, "草稿台词", null, null, ADMIN), 11L);
        adopted.adopt();
        when(drafts.findById(11L)).thenReturn(Optional.of(adopted));
        when(interventions.findByDraftId(11)).thenReturn(Optional.of(new SimulationIntervention(5, 9, 3, 11,
                "TASK_REBATE", ADMIN, "原始台词", null, "采用后的台词", null, null, InterventionMode.ADMIN_TEST)));
        var error = assertThrows(ApiException.class, () -> service.adopt(ADMIN, 5, adopt("11", 2, "另一版台词")));
        assertEquals("IDEMPOTENCY_CONFLICT", error.code);
    }

    @Test
    void rejectedContentKeepsTheDraftAndNeverAdvancesTheVictim() {
        when(drafts.findActiveByRoundId(9)).thenReturn(Optional.of(draft));
        doThrow(ApiException.field("content", "CONTENT_REJECTED", "内容与上一轮骗子台词完全相同"))
                .when(validator).validate(any(), any(), any(), any());
        var error = assertThrows(ApiException.class, () -> service.adopt(ADMIN, 5, adopt("11", 2, "未通过的改写台词")));
        assertEquals("VALIDATION_FAILED", error.code);
        assertEquals(ScammerDraftStatus.DRAFT, draft.getStatus());
        assertEquals(2, draft.getRevisionNo());
        verify(transitions, never()).adoptDraft(anyLong(), anyInt(), anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void unknownOrForeignSessionIsANotFound() {
        assertThrows(ApiException.class, () -> service.state(ADMIN + 1, 5));
        assertThrows(ApiException.class, () -> service.adopt(ADMIN + 1, 5, adopt("11", 2, null)));
        assertThrows(ApiException.class, () -> service.revise(ADMIN + 1, 5, new DraftRequest("11", 2, null, "修改台词")));
        assertThrows(ApiException.class, () -> service.regenerate(ADMIN + 1, 5, new DraftRequest("11", 2, null, null)));
        verifyNoInteractions(transitions);
        var userMode = new SimulationSession(ADMIN, "req", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4, "{}", NOW, NOW, "{}", 1000);
        when(sessions.findById(6L)).thenReturn(Optional.of(userMode));
        assertThrows(ApiException.class, () -> service.state(ADMIN, 6), "a legacy automatic session has no review gate");
    }

    /** Entities get their generated id only from the database; tests pin it to keep the id checks real. */
    private static <T> T withId(T entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }}
