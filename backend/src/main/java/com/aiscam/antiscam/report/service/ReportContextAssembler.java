package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Only frozen snapshots and persisted facts may enter this context; never log the result. */
public final class ReportContextAssembler {
    private ReportContextAssembler() {}

    public static Map<String, String> variables(SimulationSession session, List<SimulationRound> rounds,
            Map<Long, List<SimulationMessage>> messagesByRound, ConfigurationSnapshots snapshots) {
        var scenario = snapshots.read(session.getScenarioSnapshot());
        String outcome = session.getOutcomeCode().displayText() + "（" + session.getOutcomeCode().name() + "）："
                + session.getOutcomeReason() + (session.getLimitReason() == null ? "" : "；限制：" + session.getLimitReason().name());
        var text = new StringBuilder();
        for (var round : rounds.stream().sorted(Comparator.comparingInt(SimulationRound::getRoundNo)).toList()) {
            text.append("第").append(round.getRoundNo()).append("轮｜");
            for (var m : messagesByRound.getOrDefault(round.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(SimulationMessage::getSpeakerRole)).toList()) {
                boolean scammer = m.getSpeakerRole() == SpeakerRole.SCAMMER;
                text.append(scammer ? "骗子：" : "受害者：").append(m.getContent()).append("（");
                if (m.getActionText() != null) text.append("行动：").append(m.getActionText()).append("；");
                text.append(scammer ? "内部意图：" : "内部心理：").append(m.internalNoteForAgents()).append("）／");
            }
            if (round.hasAnalysis()) {
                text.append("解说：阶段：").append(round.getFraudStage()).append("；话术：").append(round.getSpeechAnalysis())
                        .append("；手段：").append(round.getMethodAnalysis()).append("；心理：").append(round.getVictimPsychology())
                        .append("；风险分：").append(round.getRiskScore()).append("；依据：").append(round.getRiskReason());
            } else text.append("本轮无解说");
            text.append('\n');
        }
        return Map.of("scenario", scenario.path("name").asText() + "：" + scenario.path("description").asText(),
                "profiles", "骗子：\n" + snapshots.scammerText(session.getScammerSnapshot()) + "\n受害者：\n"
                        + snapshots.victimText(session.getVictimSnapshot()), "outcome", outcome, "rounds", text.toString());
    }
}
