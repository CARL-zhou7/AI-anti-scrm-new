package com.aiscam.antiscam.simulation.web.dto;

import com.aiscam.antiscam.simulation.domain.*;
import java.util.List;

/** Administrator intervention DTOs (interface spec API-47～49). No internal note or snapshot leaks into these. */
public final class InterventionViews {
    private InterventionViews() {}

    public record ScammerDraft(String draftId, String sessionId, int roundNo, String roundPhase, int revisionNo,
            String status, String originalContent, String originalAction, String draftContent, String draftAction,
            String guidance, String updatedAt) {
        public static ScammerDraft from(SimulationScammerDraft d, RoundPhase phase) {
            return new ScammerDraft(d.getId().toString(), Long.toString(d.getSessionId()), d.getRoundNo(),
                    phase == null ? null : phase.name(), d.getRevisionNo(), d.getStatus().name(),
                    d.getOriginalContent(), d.getOriginalAction(), d.getDraftContent(), d.getDraftAction(),
                    d.getGuidanceText(), com.aiscam.antiscam.common.time.UtcTime.format(d.getUpdatedAt()));
        }
    }

    /** {@code phase} is null before round 1; {@code currentDraft} is only present while the round is under review. */
    public record InterventionState(String sessionId, String interventionMode, int currentRoundNo, String phase,
            ScammerDraft currentDraft, int pendingInterventionCount, boolean canRegenerate, boolean canAdopt) {}

    public record DraftRequest(String draftId, int expectedRevisionNo, String guidance, String content) {}

    /** Java cannot name a component {@code continue}, so the wire field is pinned explicitly. */
    public record AdoptRequest(String draftId, int expectedRevisionNo,
            @com.fasterxml.jackson.annotation.JsonProperty("continue") Boolean continueAdopt, String content) {}

    public record InterventionDraftEvent(int roundNo, ScammerDraft draft, int pendingInterventionCount) {}

    public record InterventionRecord(String interventionId, String sessionId, String sourceSessionId, int roundNo,
            String sceneCode, String originalContent, String finalContent, String interventionNote, String status,
            String createdAt, String ruleId, String previousContext) {
        public static InterventionRecord from(SimulationIntervention i) { return from(i, null); }

        /** {@code ruleId} is only filled for the sources of one rule; a waiting record belongs to no rule yet. */
        public static InterventionRecord from(SimulationIntervention i, String ruleId) {
            return new InterventionRecord(i.getId().toString(), Long.toString(i.getSessionId()),
                    Long.toString(i.getSessionId()), i.getRoundNo(), i.getSceneCode(), i.getOriginalContent(),
                    i.getFinalContent(), i.getInterventionNote(), i.getStatus().name(),
                    com.aiscam.antiscam.common.time.UtcTime.format(i.getCreatedAt()), ruleId, i.getPreviousContext());
        }
    }

    public record InterventionPage(List<InterventionRecord> items, String nextCursor) {}
}
