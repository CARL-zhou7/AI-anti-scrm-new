package com.aiscam.antiscam.simulation.web.dto;

import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots.ConfigurationView;
import java.util.List;

/**
 * User-facing simulation DTOs (interface spec §7). None of these records has a slot for the internal note,
 * runtime snapshot, timing evidence or prompt text, so those can never be serialized to a user.
 */
public final class SimulationViews {
    private SimulationViews() {}

    public record State(String sessionId, String status, String stateVersion, String startedAt, String deadlineAt,
            String endedAt, int currentRoundNo, ConfigurationView configuration, LiveRound currentRound,
            Integer latestRiskScore, Outcome outcome, Interruption interruption, String readingUntil, String nextAction,
            boolean paused, String pausedAt, int pausedCreditMs, ReportSummary report, Funds funds,
            String winner, Integer finalTrustScore,
            /** Present for review sessions, including USER sessions created during private testing. */
            com.aiscam.antiscam.simulation.web.dto.InterventionViews.InterventionState intervention,
            /** How many scene rules this session froze at start; the rule text itself is never public. */
            int ruleSnapshotCount) {}

    public record Funds(boolean fundsRecorded, Integer initialAvailableYuan, Integer availableBalanceYuan,
            Integer totalPaidYuan, Integer totalRefundedYuan, Integer actualLossYuan, Integer additionalPaymentCount,
            Integer reserveReleasedYuan, List<FundFlow> flows, Integer totalRewardedYuan, Integer generalReserveTotalYuan,
            Integer generalReserveRemainingYuan, Integer emergencyReserveTotalYuan, Integer emergencyReserveRemainingYuan) {
        public Funds(boolean recorded, Integer initial, Integer balance, Integer paid, Integer refunded, Integer loss,
                Integer additional, Integer released, List<FundFlow> flows) {
            this(recorded,initial,balance,paid,refunded,loss,additional,released,flows,null,null,null,null,null);
        }
        public static Funds from(com.aiscam.antiscam.simulation.domain.SimulationSession session, boolean history) {
            return from(session,history,false);
        }
        public static Funds from(com.aiscam.antiscam.simulation.domain.SimulationSession session, boolean history, boolean v3) {
            if (session.getSnapshotSchemaVersion() < 2) {
                return new Funds(false, null, null, null, null, null, null, null, history ? List.of() : null);
            }
            var f = session.getFundsSummary();
            return new Funds(true, f.initialAvailableYuan(), f.availableBalanceYuan(), f.totalPaidYuan(),
                    f.totalRefundedYuan(), f.actualLossYuan(), f.additionalPaymentCount(), f.reserveReleasedYuan(),
                    history ? List.of() : null, v3 ? f.totalRewardedYuan() : null,
                    v3 ? session.getGeneralReserveTotalYuan() : null,
                    v3 ? session.getGeneralReserveTotalYuan()-f.generalReserveReleasedYuan() : null,
                    v3 ? session.getEmergencyReserveTotalYuan() : null,
                    v3 ? session.getEmergencyReserveTotalYuan()-f.emergencyReserveReleasedYuan() : null);
        }
    }

    public record FundFlow(int sequenceNo, int roundNo, String messageId, String type, String purpose,
            int amountYuan, int balanceAfterYuan, String reason, String occurredAt) {}

    public record LiveRound(int roundNo, String phase, boolean interactionComplete, boolean presentationComplete,
            List<LiveMessage> messages, Analysis analysis, String readingUntil) {}

    public record LiveMessage(String id, int roundNo, String speaker, String content, String actionText,
            String presentationStatus, String presentationCompletedAt, String presentationErrorCode, String savedAt,
            String audioUrl, Long audioDurationMs, List<AudioSegment> segments) {}

    public record AudioSegment(long startMs, long endMs, int charStart, int charEnd) {}

    /** {@code methodShort}/{@code intentShort} feed the real-time card only; reports read the full fields. */
    public record Analysis(String fraudStage, String speechAnalysis, String methodAnalysis, String victimPsychology,
            int riskScore, String riskReason, String savedAt, String presentedAt, TrustChange trustChange,
            String methodShort, String intentShort) {
        public Analysis(String fraudStage, String speechAnalysis, String methodAnalysis, String victimPsychology,
                int riskScore, String riskReason, String savedAt, String presentedAt, TrustChange trustChange) {
            this(fraudStage, speechAnalysis, methodAnalysis, victimPsychology, riskScore, riskReason, savedAt, presentedAt,
                    trustChange, null, null);
        }
    }

    public record TrustChange(int before, int after, int delta) {}

    public record Outcome(String code, String displayText, String reason, String limitReason,
            Boolean goalAchieved, Boolean stoppedAfterFraud, Integer actualLossYuan, String winning,
            Integer finalTrustScore) {
        public Outcome(String code, String displayText, String reason, String limitReason, Boolean goalAchieved,
                Boolean stoppedAfterFraud, Integer actualLossYuan) {
            this(code, displayText, reason, limitReason, goalAchieved, stoppedAfterFraud, actualLossYuan, null, null);
        }
    }

    public record Interruption(String code, String message) {}

    public record ReportSummary(String status, String displayText, boolean canRetry, int attemptNo, String updatedAt,
            ReportError error) {}

    public record ReportError(String code, String message) {}

    public record RiskPoint(int roundNo, int score, TrustChange trustChange) {
        public RiskPoint(int roundNo, int score) { this(roundNo, score, null); }
    }

    public record Ending(String code, String displayText, String reason) {}

    public record ReportView(String sessionId, String simulationStatus, ReportSummary report, String unavailableReason,
            Outcome outcome, com.aiscam.antiscam.agent.AgentModels.ReportData content, List<RiskPoint> riskSeries, Integer contentSchemaVersion, Ending ending) {
        public ReportView(String sessionId, String status, ReportSummary report, String unavailable, Outcome outcome,
                com.aiscam.antiscam.agent.AgentModels.ReportData content, List<RiskPoint> risks, Integer version) {
            this(sessionId,status,report,unavailable,outcome,content,risks,version,null);
        }
    }

    public record SessionStateData(State state, ReportView reportView) {}

    public record RoundStarted(int roundNo, String phase, String startedAt, String deadlineAt) {}

    public record AnalysisSaved(int roundNo, Analysis analysis) {}

    public record RoundReading(int roundNo, String readingUntil, String nextAction) {}

    public record EffectResult(String effect, State state) {}

    public record PresentationReceipt(String status, String errorCode) {}

    public record ExitRequest(String reason) {}

    public record StartRequest(String startRequestId, String scenarioId, String scammerProfileId, String victimProfileId,
            com.aiscam.antiscam.timing.TimingCalibrator.TimingEvidence timing) {}
}
