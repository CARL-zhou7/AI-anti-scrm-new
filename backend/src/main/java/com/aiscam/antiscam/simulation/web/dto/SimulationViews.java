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
            boolean paused, String pausedAt, int pausedCreditMs, ReportSummary report) {}

    public record LiveRound(int roundNo, String phase, boolean interactionComplete, boolean presentationComplete,
            List<LiveMessage> messages, Analysis analysis, String readingUntil) {}

    public record LiveMessage(String id, int roundNo, String speaker, String content, String actionText,
            String presentationStatus, String presentationCompletedAt, String presentationErrorCode, String savedAt,
            String audioUrl, Long audioDurationMs, List<AudioSegment> segments) {}

    public record AudioSegment(long startMs, long endMs, int charStart, int charEnd) {}

    public record Analysis(String fraudStage, String speechAnalysis, String methodAnalysis, String victimPsychology,
            int riskScore, String riskReason, String savedAt, String presentedAt) {}

    public record Outcome(String code, String displayText, String reason, String limitReason) {}

    public record Interruption(String code, String message) {}

    public record ReportSummary(String status, String displayText, boolean canRetry, int attemptNo, String updatedAt,
            Object error) {}

    public record RiskPoint(int roundNo, int score) {}

    public record ReportView(String sessionId, String simulationStatus, ReportSummary report, String unavailableReason,
            Outcome outcome, Object content, List<RiskPoint> riskSeries) {}

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
