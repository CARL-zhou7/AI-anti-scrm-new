package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.*;
import org.springframework.stereotype.Component;

/**
 * Builds one {@link InterventionState} for both the REST endpoint and {@code SimulationState.intervention}, so the
 * controller and the state assembler can never disagree about the review phase.
 */
@Component
public class InterventionStateAssembler {
    private final SimulationScammerDraftRepository drafts;
    private final SimulationRoundRepository rounds;
    private final SimulationInterventionRepository interventions;

    public InterventionStateAssembler(SimulationScammerDraftRepository drafts, SimulationRoundRepository rounds,
            SimulationInterventionRepository interventions) {
        this.drafts = drafts;
        this.rounds = rounds;
        this.interventions = interventions;
    }

    public InterventionState assemble(SimulationSession session, ScammerDraft draft) {
        var round = session.getCurrentRoundNo() == 0 ? null
                : rounds.findBySessionIdAndRoundNo(session.getId(), session.getCurrentRoundNo()).orElse(null);
        RoundPhase phase = round == null ? null : round.getPhase();
        boolean reviewing = phase == RoundPhase.SCAMMER_REVIEW;
        return new InterventionState(session.getId().toString(), session.getInterventionMode().name(),
                session.getCurrentRoundNo(), phase == null ? null : phase.name(),
                reviewing ? draft : null,
                (int) interventions.countBySessionIdAndStatus(session.getId(), InterventionStatus.ADOPTED),
                reviewing && draft != null, reviewing && draft != null);
    }

    /** Draft projection for the current round while it waits for review, otherwise null. */
    public ScammerDraft currentDraft(SimulationSession session) {
        if (!session.isAdminTest() || session.getCurrentRoundNo() == 0) return null;
        var round = rounds.findBySessionIdAndRoundNo(session.getId(), session.getCurrentRoundNo()).orElse(null);
        if (round == null || round.getPhase() != RoundPhase.SCAMMER_REVIEW) return null;
        return drafts.findActiveByRoundId(round.getId()).map(d -> ScammerDraft.from(d, round.getPhase())).orElse(null);
    }
}
