package com.aiscam.antiscam.history.service;

import static com.aiscam.antiscam.common.time.UtcTime.format;
import static com.aiscam.antiscam.simulation.service.SimulationStateAssembler.interruption;
import static com.aiscam.antiscam.simulation.service.SimulationStateAssembler.outcome;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.history.web.dto.HistoryViews.*;
import com.aiscam.antiscam.report.service.ReportViewMapper;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SimulationStateAssembler;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HistoryAssembler {
    private static final Logger log = LoggerFactory.getLogger(HistoryAssembler.class);
    private final ConfigurationSnapshots snapshots;
    private final SimulationStateAssembler state;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final SimulationReportRepository reports;

    public HistoryAssembler(ConfigurationSnapshots snapshots, SimulationStateAssembler state,
            SimulationRoundRepository rounds, SimulationMessageRepository messages, SimulationReportRepository reports) {
        this.snapshots = snapshots;
        this.state = state;
        this.rounds = rounds;
        this.messages = messages;
        this.reports = reports;
    }

    public HistoryItem item(SimulationSession s, SimulationReport row) {
        var names = snapshots.names(s.getScenarioSnapshot(), s.getScammerSnapshot(), s.getVictimSnapshot());
        boolean finished = s.getStatus() == SimulationStatus.FINISHED;
        if (finished && row == null) log.warn("History report missing: sessionId={}", s.getId());
        return new HistoryItem(s.getId().toString(), format(s.getStartedAt()), names.scenario(), names.scammer(),
                names.victim(), s.getStatus().name(), finished && row != null ? ReportViewMapper.summary(row) : null,
                outcome(s), interruption(s));
    }

    public HistoryPage page(List<HistoryItem> items, String nextCursor, boolean hasMore) {
        return new HistoryPage(items, nextCursor, hasMore);
    }

    public HistoryDetail detail(SimulationSession s) {
        var report = s.getStatus() == SimulationStatus.FINISHED
                ? ReportViewMapper.summary(reports.findById(s.getId())
                        .orElseThrow(() -> ApiException.dataInconsistent("记录状态异常，请稍后重试"))) : null;
        var savedRounds = rounds.findBySessionIdOrderByRoundNoAsc(s.getId());
        var byRound = savedRounds.isEmpty() ? java.util.Map.<Long, List<SimulationMessage>>of()
                : messages.findByRoundIdIn(savedRounds.stream().map(SimulationRound::getId).toList()).stream()
                        .collect(Collectors.groupingBy(SimulationMessage::getRoundId));
        var historyRounds = savedRounds.stream().map(r -> {
            var list = byRound.getOrDefault(r.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(m -> m.getSpeakerRole().ordinal()))
                    .map(m -> new HistoryMessage(m.getId().toString(), r.getRoundNo(), m.getSpeakerRole().name(),
                            m.getContent(), m.getActionText(), m.getPresentationStatus().name(),
                            format(m.getPresentationCompletedAt()), m.getPresentationErrorCode(), format(m.getCreatedAt())))
                    .toList();
            return new HistoryRound(r.getRoundNo(), r.getPhase().name(), list.size() == 2,
                    r.getPhase() == RoundPhase.COMPLETE, list, state.analysis(r), format(r.getReadingUntil()));
        }).toList();
        return new HistoryDetail(s.getId().toString(), s.getStatus().name(), format(s.getStartedAt()),
                format(s.getDeadlineAt()), format(s.getEndedAt()),
                snapshots.toConfigurationView(s.getScenarioSnapshot(), s.getScammerSnapshot(), s.getVictimSnapshot()),
                historyRounds, outcome(s), interruption(s), report);
    }
}
