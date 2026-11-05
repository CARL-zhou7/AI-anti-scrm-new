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

    public enum AgentRole { SCAMMER, VICTIM, NARRATOR, REPORT, EVENT, VICTIM_RULE }

    public enum Speed { SLOW, NORMAL, FAST }

    public enum EvidenceSource { CONTENT, ACTION }

    public record TrustEventCandidate(com.aiscam.antiscam.simulation.rules.TrustEventType type,
            String subjectKey, String quote, EvidenceSource source, List<Material> materials, Requirement requirement,
            String progressKey, com.aiscam.antiscam.simulation.rules.CounterevidenceLevel counterevidenceLevel,
            SpeakerRole speaker, Integer eventOrder) {
        public TrustEventCandidate(com.aiscam.antiscam.simulation.rules.TrustEventType type,
                String subjectKey, String quote, EvidenceSource source, List<Material> materials, Requirement requirement,
                String progressKey) {
            this(type,subjectKey,quote,source,materials,requirement,progressKey,null,null,null);
        }
        public TrustEventCandidate(com.aiscam.antiscam.simulation.rules.TrustEventType type,
                String subjectKey, String quote, EvidenceSource source, List<Material> materials, Requirement requirement) {
            this(type,subjectKey,quote,source,materials,requirement,null,null,null,null);
        }
        public TrustEventCandidate(com.aiscam.antiscam.simulation.rules.TrustEventType type,
                String subjectKey, String quote, EvidenceSource source) { this(type,subjectKey,quote,source,List.of(),null); }
    }
    public record Material(String subjectKey, int amountYuan, String date, String accountName, String purpose) {}
    public record Requirement(com.aiscam.antiscam.simulation.domain.WaitingItemType type, String subjectKey, String text, String basis) {}
    public enum WaitingAction { OPEN, RESPOND, ABANDON }
    public record WaitingUpdate(WaitingAction action, com.aiscam.antiscam.simulation.domain.WaitingItemType type,
            String subjectKey, String text, String basis) {}

    public record ToneHint(Speed speed, String style) {
        /** Tone only maps to synthesis speed; the text itself is never changed. */
        public double ttsSpeed() { return speed == Speed.SLOW ? 0.9 : speed == Speed.FAST ? 1.1 : 1.0; }
    }

    public record ScammerTurn(String line, String action, String intent, ToneHint tone, Integer claimedRefundYuan, List<FundAction> fundActions) {}

    public record VictimTurn(String line, String action, String psychology, ToneHint tone, List<FundAction> fundActions) {}

    public record RoundAnalysis(int roundNo, String analysis) {}

    public sealed interface ReportData permits ReportContent, ReportContentV2, ReportContentV3, ReportContentV4 {
        String summary();
        int contentSchemaVersion();
    }

    public record ReportResult(String text, String reason) {}
    public record ReportKeyNode(int roundNo, String quote, String analysis, String title) {
        public ReportKeyNode(int roundNo, String quote, String analysis) { this(roundNo, quote, analysis, null); }
    }
    public record ReportFundsSummary(int totalPaidYuan, int totalRefundedYuan, int actualLossYuan) {
        public static ReportFundsSummary from(com.aiscam.antiscam.simulation.domain.SimulationSession session) {
            return new ReportFundsSummary(session.getTotalPaidYuan(), session.getTotalRefundedYuan(), session.getActualLossYuan());
        }
    }
    public record ReportContentV2(int contentSchemaVersion, ReportResult result, String summary,
            List<ReportKeyNode> keyNodes, List<String> preventionAdvice, ReportFundsSummary fundsSummary) implements ReportData {}

    public record ReportFundsSummaryV3(int totalPaidYuan, int totalRefundedYuan, int totalRewardedYuan,
            int actualLossYuan, int availableBalanceYuan, int generalReserveRemainingYuan, int emergencyReserveRemainingYuan) {
        public static ReportFundsSummaryV3 from(com.aiscam.antiscam.simulation.domain.SimulationSession s) {
            return new ReportFundsSummaryV3(s.getTotalPaidYuan(), s.getTotalRefundedYuan(), s.getTotalRewardedYuan(),
                    s.getActualLossYuan(), s.getAvailableBalanceYuan(), s.getGeneralReserveTotalYuan()-s.getGeneralReserveReleasedYuan(),
                    s.getEmergencyReserveTotalYuan()-s.getEmergencyReserveReleasedYuan());
        }
    }
    public record ReportContentV3(int contentSchemaVersion, ReportResult result, String summary,
            List<ReportKeyNode> keyNodes, List<String> preventionAdvice, ReportFundsSummaryV3 fundsSummary) implements ReportData {}

    /**
     * V1.4 report: the same structure as v3 plus the program-decided verdict. {@code winning} and
     * {@code finalTrustScore} are always injected from the saved session, never taken from the model; both are null
     * for a user exit.
     */
    public record ReportContentV4(int contentSchemaVersion, ReportResult result, String winning,
            Integer finalTrustScore, String summary, List<ReportKeyNode> keyNodes, List<String> preventionAdvice,
            ReportFundsSummaryV3 fundsSummary) implements ReportData {}

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
            OutcomeCode candidateOutcome, String candidateReason, boolean characterExit, String characterExitReason,
            List<TrustEventCandidate> attitudeEvents, List<WaitingUpdate> waitingUpdates,
            String methodShort, String intentShort) {
        public NarratorJudgement(String fraudStage, String speechAnalysis, String methodAnalysis, String victimPsychology,
                int riskScore, String riskReason, boolean actualFraudOccurred, boolean goalAchieved, boolean cooperationStopped,
                List<EvidenceItem> evidence, OutcomeCode candidateOutcome, String candidateReason, boolean characterExit,
                String characterExitReason, List<TrustEventCandidate> attitudeEvents, List<WaitingUpdate> waitingUpdates) {
            this(fraudStage,speechAnalysis,methodAnalysis,victimPsychology,riskScore,riskReason,actualFraudOccurred,goalAchieved,
                    cooperationStopped,evidence,candidateOutcome,candidateReason,characterExit,characterExitReason,attitudeEvents,
                    waitingUpdates,null,null);
        }
        public NarratorJudgement(String fraudStage, String speechAnalysis, String methodAnalysis, String victimPsychology,
                int riskScore, String riskReason, boolean actualFraudOccurred, boolean goalAchieved, boolean cooperationStopped,
                List<EvidenceItem> evidence, OutcomeCode candidateOutcome, String candidateReason, boolean characterExit,
                String characterExitReason, List<TrustEventCandidate> attitudeEvents) {
            this(fraudStage,speechAnalysis,methodAnalysis,victimPsychology,riskScore,riskReason,actualFraudOccurred,goalAchieved,
                    cooperationStopped,evidence,candidateOutcome,candidateReason,characterExit,characterExitReason,attitudeEvents,List.of());
        }
        public NarratorJudgement(String fraudStage, String speechAnalysis, String methodAnalysis, String victimPsychology,
                int riskScore, String riskReason, boolean actualFraudOccurred, boolean goalAchieved,
                boolean cooperationStopped, List<EvidenceItem> evidence, OutcomeCode candidateOutcome,
                String candidateReason, boolean characterExit, String characterExitReason) {
            this(fraudStage, speechAnalysis, methodAnalysis, victimPsychology, riskScore, riskReason, actualFraudOccurred,
                    goalAchieved, cooperationStopped, evidence, candidateOutcome, candidateReason, characterExit,
                    characterExitReason, List.of());
        }
    }

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
            String victimTemplate, String narratorTemplate, String victimPublicProfile, boolean v12,
            String eventTemplate, boolean trustV1, boolean trustV2, boolean trustV3,
            /** Enabled scene rules frozen at session start, rendered as text; the victim never receives them. */
            String sceneRules) {
        public AgentScene(ScenarioCode scenarioCode, String scenarioName, String scenarioDescription,
                String scammerProfile, String victimProfile, String scriptKey, String scammerTemplate,
                String victimTemplate, String narratorTemplate, String victimPublicProfile, boolean v12,
                String eventTemplate, boolean trustV1, boolean trustV2, boolean trustV3) {
            this(scenarioCode,scenarioName,scenarioDescription,scammerProfile,victimProfile,scriptKey,scammerTemplate,
                    victimTemplate,narratorTemplate,victimPublicProfile,v12,eventTemplate,trustV1,trustV2,trustV3,null);
        }
        public AgentScene(ScenarioCode scenarioCode, String scenarioName, String scenarioDescription,
                String scammerProfile, String victimProfile, String scriptKey, String scammerTemplate,
                String victimTemplate, String narratorTemplate, String victimPublicProfile, boolean v12,
                String eventTemplate, boolean trustV1, boolean trustV2) {
            this(scenarioCode,scenarioName,scenarioDescription,scammerProfile,victimProfile,scriptKey,scammerTemplate,
                    victimTemplate,narratorTemplate,victimPublicProfile,v12,eventTemplate,trustV1,trustV2,false);
        }
        public AgentScene(ScenarioCode scenarioCode, String scenarioName, String scenarioDescription,
                String scammerProfile, String victimProfile, String scriptKey, String scammerTemplate,
                String victimTemplate, String narratorTemplate, String victimPublicProfile, boolean v12,
                String eventTemplate, boolean trustV1) {
            this(scenarioCode,scenarioName,scenarioDescription,scammerProfile,victimProfile,scriptKey,scammerTemplate,
                    victimTemplate,narratorTemplate,victimPublicProfile,v12,eventTemplate,trustV1,false);
        }
        public AgentScene(ScenarioCode scenarioCode, String scenarioName, String scenarioDescription,
                String scammerProfile, String victimProfile, String scriptKey, String scammerTemplate,
                String victimTemplate, String narratorTemplate, String victimPublicProfile, boolean v12) {
            this(scenarioCode, scenarioName, scenarioDescription, scammerProfile, victimProfile, scriptKey,
                    scammerTemplate, victimTemplate, narratorTemplate, victimPublicProfile, v12, null, false);
        }
    }
}
