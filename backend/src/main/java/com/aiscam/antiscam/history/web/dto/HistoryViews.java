package com.aiscam.antiscam.history.web.dto;

import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots.ConfigurationView;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.*;
import java.util.List;

public final class HistoryViews {
    private HistoryViews() {}

    public record HistoryItem(String sessionId, String startedAt, String scenarioName, String scammerName,
            String victimName, String status, ReportSummary report, Outcome outcome, Interruption interruption) {}
    public record HistoryPage(List<HistoryItem> items, String nextCursor, boolean hasMore) {}
    public record HistoryMessage(String id, int roundNo, String speaker, String content, String actionText,
            String presentationStatus, String presentationCompletedAt, String presentationErrorCode, String savedAt) {}
    public record HistoryRound(int roundNo, String phase, boolean interactionComplete, boolean presentationComplete,
            List<HistoryMessage> messages, Analysis analysis, String readingUntil) {}
    public record HistoryDetail(String sessionId, String status, String startedAt, String deadlineAt, String endedAt,
            ConfigurationView configuration, List<HistoryRound> rounds, Outcome outcome,
            Interruption interruption, ReportSummary report) {}
}
