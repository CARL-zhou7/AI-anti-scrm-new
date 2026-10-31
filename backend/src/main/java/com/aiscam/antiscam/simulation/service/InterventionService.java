package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.*;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.State;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The round review gate (API-47～49). Every entry first proves ownership and ADMIN_TEST mode, then checks the
 * round and draft revision, and only then touches content. Content validation runs outside the transaction; the
 * commit re-checks the draft id/revision under the session lock, so a late request cannot advance the victim.
 */
@Service
public class InterventionService {
    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final SimulationScammerDraftRepository drafts;
    private final SimulationInterventionRepository interventions;
    private final SimulationTransitions transitions;
    private final InterventionStateAssembler assembler;
    private final SimulationStateAssembler stateAssembler;
    private final DraftContentValidator validator;

    public InterventionService(SimulationSessionRepository sessions, SimulationRoundRepository rounds,
            SimulationMessageRepository messages, SimulationScammerDraftRepository drafts,
            SimulationInterventionRepository interventions, SimulationTransitions transitions,
            InterventionStateAssembler assembler, SimulationStateAssembler stateAssembler,
            DraftContentValidator validator) {
        this.interventions = interventions;
        this.sessions = sessions;
        this.rounds = rounds;
        this.messages = messages;
        this.drafts = drafts;
        this.transitions = transitions;
        this.assembler = assembler;
        this.stateAssembler = stateAssembler;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public InterventionState state(long userId, long sessionId) {
        var session = ownedAdminTest(userId, sessionId);
        return assembler.assemble(session, assembler.currentDraft(session));
    }

    /** Adopt the saved draft directly; a new rewrite must pass validation before it becomes the formal line. */
    public State adopt(long userId, long sessionId, AdoptRequest request) {
        var session = ownedAdminTest(userId, sessionId);
        var replay = adoptedReplay(session, request);
        if (replay != null) return replay;
        var current = requireRequest(userId, sessionId, request.draftId(), request.expectedRevisionNo());
        String content = request.content() == null || request.content().isBlank()
                ? current.draft().getDraftContent() : request.content().strip();
        boolean edited = !content.equals(current.draft().getDraftContent());
        // Saved drafts already passed generation or revision validation; only a new rewrite needs review.
        if (edited) validator.validate(current.session(), current.draft(), content, current.history());
        transitions.adoptDraft(sessionId, current.session().getCurrentRoundNo(), current.draft().getId(),
                request.expectedRevisionNo(), content, edited ? null : current.draft().getDraftAction(), null);
        return stateAssembler.assemble(sessions.findById(sessionId).orElseThrow());
    }

    /**
     * A repeated adopt of the same, already adopted draft of the current round is answered with the existing state
     * instead of generating the victim a second time. Different final content on the same draft is a conflict.
     */
    private State adoptedReplay(SimulationSession session, AdoptRequest request) {
        if (request.draftId() == null || !request.draftId().matches("\\d+")) return null;
        var draft = drafts.findById(Long.parseLong(request.draftId())).orElse(null);
        if (draft == null || draft.getSessionId() != session.getId()
                || draft.getStatus() != ScammerDraftStatus.ADOPTED) return null;
        if (session.getCurrentRoundNo() > 0) {
            var round = rounds.findBySessionIdAndRoundNo(session.getId(), session.getCurrentRoundNo()).orElse(null);
            if (round == null || round.getId() != draft.getRoundId()) return null;
        }
        if (request.content() != null && !request.content().isBlank()) {
            String adopted = interventions.findByDraftId(draft.getId())
                    .map(SimulationIntervention::getFinalContent).orElse(draft.getDraftContent());
            if (!request.content().strip().equals(adopted)) throw ApiException.idempotencyConflict();
        }
        return stateAssembler.assemble(sessions.findById(session.getId()).orElseThrow());
    }

    /** Regenerate: discard the current draft so the engine prepares a fresh generation for the same round. */
    public InterventionState regenerate(long userId, long sessionId, DraftRequest request) {
        var current = requireRequest(userId, sessionId, request.draftId(), request.expectedRevisionNo());
        String guidance = request.guidance() == null ? "" : request.guidance().strip();
        if (guidance.length() > 200) throw ApiException.field("guidance", "TOO_LONG", "重新生成方向不能超过200字");
        transitions.discardDraft(sessionId, current.session().getCurrentRoundNo(), current.draft().getId(),
                request.expectedRevisionNo(), guidance);
        var session = sessions.findById(sessionId).orElseThrow();
        return assembler.assemble(session, assembler.currentDraft(session));
    }

    /** Direct edit without adopting: keeps the round under review with a bumped revision. */
    public InterventionState revise(long userId, long sessionId, DraftRequest request) {
        var current = requireRequest(userId, sessionId, request.draftId(), request.expectedRevisionNo());
        String content = request.content() == null || request.content().isBlank()
                ? current.draft().getDraftContent() : request.content();
        // The exact stored AI original already passed generation validation and can be restored unchanged.
        if (!content.strip().equals(current.draft().getOriginalContent())) {
            validator.validate(current.session(), current.draft(), content, current.history());
        }
        transitions.reviseDraft(sessionId, current.session().getCurrentRoundNo(), current.draft().getId(),
                request.expectedRevisionNo(), content, current.draft().getDraftAction(), request.guidance());
        var session = sessions.findById(sessionId).orElseThrow();
        return assembler.assemble(session, assembler.currentDraft(session));
    }

    private record Current(SimulationSession session, SimulationScammerDraft draft, List<SimulationMessage> history) {}

    private Current requireRequest(long userId, long sessionId, String draftId, int expectedRevisionNo) {
        var session = ownedAdminTest(userId, sessionId);
        if (!session.isRunning()) throw ApiException.stageConflict();
        int roundNo = session.getCurrentRoundNo();
        if (roundNo == 0) throw ApiException.staleDraft();
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow(ApiException::staleDraft);
        if (round.getPhase() != RoundPhase.SCAMMER_REVIEW) throw ApiException.staleDraft();
        var draft = drafts.findActiveByRoundId(round.getId()).orElseThrow(ApiException::staleDraft);
        if (draftId == null || !draft.getId().toString().equals(draftId)) throw ApiException.staleDraft();
        if (draft.getRevisionNo() != expectedRevisionNo) throw ApiException.draftRevisionConflict();
        var history = rounds.findBySessionIdOrderByRoundNoAsc(sessionId).stream()
                .filter(r -> r.getRoundNo() < roundNo || r.getId() == round.getId())
                .flatMap(r -> messages.findByRoundId(r.getId()).stream()).toList();
        return new Current(session, draft, history);
    }

    /** Only the owner may read or change an ADMIN_TEST session; anything else is a 404, never a 403 leak. */
    private SimulationSession ownedAdminTest(long userId, long sessionId) {
        var session = sessions.findById(sessionId).orElseThrow(ApiException::notFound);
        if (session.getUserId() != userId || !session.isAdminTest()) throw ApiException.notFound();
        return session;
    }
}
