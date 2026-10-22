package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.aiscam.antiscam.simulation.domain.FundAction;
import com.aiscam.antiscam.simulation.rules.FactCode;
import java.util.List;

/** Value types exchanged between the orchestration engine and the Agent execution layer. */
public final class AgentModels {
    private AgentModels() {}

    public enum AgentRole { SCAMMER, VICTIM, NARRATOR, REPORT }

    public enum Speed { SLOW, NORMAL, FAST }

    public enum EvidenceSource { CONTENT, ACTION }

    public record ToneHint(Speed speed, String style) {
        /** Tone only maps to synthesis speed; the text itself is never changed. */
        public double ttsSpeed() { return speed == Speed.SLOW ? 0.9 : speed == Speed.FAST ? 1.1 : 1.0; }
    }

    public record ScammerTurn(String line, String action, String intent, ToneHint tone, Integer claimedRefundYuan, List<FundAction> fundActions) {}

    public record VictimTurn(String line, String action, String psychology, ToneHint tone, List<FundAction> fundActions) {}

    public record RoundAnalysis(int roundNo, String analysis) {}

    public sealed interface ReportData permits ReportContent, ReportContentV2 {
        String summary();
        int contentSchemaVersion();
    }

    public record ReportResult(String text, String reason) {}
    public record ReportKeyNode(int roundNo, String quote, String analysis) {}
    public record ReportFundsSummary(int totalPaidYuan, int totalRefundedYuan, int actualLossYuan) {
        public static ReportFundsSummary from(com.aiscam.antiscam.simulation.domain.SimulationSession session) {
            return new ReportFundsSummary(session.getTotalPaidYuan(), session.getTotalRefundedYuan(), session.getActualLossYuan());
        }
    }
    public record ReportContentV2(int contentSchemaVersion, ReportResult result, String summary,
            List<ReportKeyNode> keyNodes, List<String> preventionAdvice, ReportFundsSummary fundsSummary) implements ReportData {}

    public record ReportContent(String summary, List<RoundAnalysis> roundAnalyses, String speechAnalysis,
            String victimBehaviorAnalysis, String outcomeAnalysis) implements ReportData {
        @com.fasterxml.jackson.annotation.JsonProperty("contentSchemaVersion")
        public int contentSchemaVersion() { return 1; }
    }

    public record EvidenceItem(FactCode factCode, SpeakerRole speaker, EvidenceSource source, int roundNo, String quote,
            int eventOrder) {}

    public record NarratorJudgement(String fraudStage, String speechAnalysis, String methodAnalysis,
            String victimPsychology, int riskScore, String riskReason, boolean actualFraudOccurred,
            boolean goalAchieved, boolean cooperationStopped, List<EvidenceItem> evidence,
            OutcomeCode candidateOutcome, String candidateReason, boolean characterExit, String characterExitReason) {}

    /** A judgement that passed validation, with the evidence carried forward from earlier rounds merged in. */
    public record ValidatedJudgement(NarratorJudgement judgement, List<EvidenceItem> mergedEvidence) {}

    /** One already-spoken (or candidate) line; the internal note only ever reaches the narrator. */
    public record DialogueLine(int roundNo, SpeakerRole speaker, String content, String action, String internalNote) {}

    /** Facts saved by the previous analysed round. */
    public record PriorFacts(boolean actualFraud, boolean goalAchieved, List<EvidenceItem> evidence) {
        public static PriorFacts none() { return new PriorFacts(false, false, List.of()); }
    }

    /** Per-session constant inputs: frozen snapshots (rendered as text) and the pinned prompt versions. */
    public record AgentScene(ScenarioCode scenarioCode, String scenarioName, String scenarioDescription,
            String scammerProfile, String victimProfile, String scriptKey, String scammerTemplate,
            String victimTemplate, String narratorTemplate, String victimPublicProfile, boolean v12) {}
}
