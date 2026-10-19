package com.aiscam.antiscam.simulation.service;

import static com.aiscam.antiscam.common.time.UtcTime.format;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.report.service.ReportViewMapper;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.*;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** The only place a SimulationState is built; HTTP responses and SSE frames share it. Call inside a transaction. */
@Component
public class SimulationStateAssembler {
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final SimulationReportRepository reports;
    private final ConfigurationSnapshots snapshots;
    private final TempAudioStore audio;

    public SimulationStateAssembler(SimulationRoundRepository rounds, SimulationMessageRepository messages,
            SimulationReportRepository reports, ConfigurationSnapshots snapshots, TempAudioStore audio) {
        this.rounds = rounds;
        this.messages = messages;
        this.reports = reports;
        this.snapshots = snapshots;
        this.audio = audio;
    }

    public State assemble(SimulationSession s) {
        List<SimulationRound> all = rounds.findBySessionIdOrderByRoundNoAsc(s.getId());
        SimulationRound current = all.stream().filter(r -> r.getRoundNo() == s.getCurrentRoundNo()).findFirst().orElse(null);
        Integer latestRisk = all.stream().filter(SimulationRound::hasAnalysis)
                .max(Comparator.comparingInt(SimulationRound::getRoundNo)).map(SimulationRound::getRiskScore).orElse(null);
        String nextAction = null;
        if (current != null && current.getReadingUntil() != null) {
            if (s.getStatus() == SimulationStatus.FINISHED) nextAction = "REPORT";
            else if (s.isRunning() && current.getPhase() == RoundPhase.COMPLETE) nextAction = "NEXT_ROUND";
        }
        Outcome outcome = s.getStatus() == SimulationStatus.FINISHED ? outcome(s) : null;
        Interruption interruption = interruption(s);
        ReportSummary report = reportView(s).report();
        return new State(s.getId().toString(), s.getStatus().name(), Long.toString(s.getStateVersion()), format(s.getStartedAt()),
                format(s.getDeadlineAt()), format(s.getEndedAt()), s.getCurrentRoundNo(),
                snapshots.toConfigurationView(s.getScenarioSnapshot(), s.getScammerSnapshot(), s.getVictimSnapshot()),
                current == null ? null : liveRound(s, current), latestRisk, outcome, interruption,
                current == null ? null : format(current.getReadingUntil()), nextAction, s.isPaused(), format(s.getPausedAt()),
                s.getPausedTotalMs(), report);
    }

    public SessionStateData sessionState(SimulationSession s) {
        return new SessionStateData(assemble(s), reportView(s));
    }

    public ReportView reportView(SimulationSession s) {
        List<RiskPoint> series = s.getStatus() == SimulationStatus.FINISHED
                ? rounds.findBySessionIdOrderByRoundNoAsc(s.getId()).stream().filter(SimulationRound::hasAnalysis)
                        .map(r -> new RiskPoint(r.getRoundNo(), r.getRiskScore())).toList()
                : List.of();
        String unavailable = switch (s.getStatus()) {
            case RUNNING -> "SIMULATION_RUNNING";
            case INTERRUPTED -> "SIMULATION_INTERRUPTED";
            case FINISHED -> null;
        };
        if (unavailable != null) return new ReportView(s.getId().toString(), s.getStatus().name(), null, unavailable, null, null, series);
        var row = reports.findById(s.getId()).orElseThrow(() -> ApiException.dataInconsistent("记录状态异常，请稍后重试"));
        var report = ReportViewMapper.summary(row);
        var content = row.getStatus() == ReportStatus.SUCCEEDED
                ? ReportViewMapper.content(row.getContentJson(), rounds.findBySessionIdOrderByRoundNoAsc(s.getId()).size(),
                        s.getOutcomeCode().displayText()) : null;
        return new ReportView(s.getId().toString(), s.getStatus().name(), report, null, outcome(s), content, series);
    }

    public LiveMessage message(SimulationSession s, SimulationMessage m, int roundNo) {
        String url = null;
        Long duration = null;
        List<AudioSegment> segments = null;
        if (s.isRunning() && m.getPresentationStatus() == PresentationStatus.PRESENTING) {
            var meta = audio.meta(s.getId(), m.getId());
            if (meta.isPresent()) {
                url = "/anti.scam/v1/simulations/" + s.getId() + "/messages/" + m.getId() + "/audio";
                duration = meta.get().durationMs();
                segments = meta.get().segments().stream()
                        .map(a -> new AudioSegment(a.startMs(), a.endMs(), a.charStart(), a.charEnd())).toList();
            }
        }
        return new LiveMessage(m.getId().toString(), roundNo, m.getSpeakerRole().name(), m.getContent(), m.getActionText(),
                m.getPresentationStatus().name(), format(m.getPresentationCompletedAt()), m.getPresentationErrorCode(),
                format(m.getCreatedAt()), url, duration, segments);
    }

    public Analysis analysis(SimulationRound r) {
        if (!r.hasAnalysis()) return null;
        return new Analysis(r.getFraudStage(), r.getSpeechAnalysis(), r.getMethodAnalysis(), r.getVictimPsychology(),
                r.getRiskScore(), r.getRiskReason(), format(r.getAnalysisSavedAt()), format(r.getAnalysisPresentedAt()));
    }

    public RoundStarted roundStarted(SimulationSession s, SimulationRound r) {
        return new RoundStarted(r.getRoundNo(), r.getPhase().name(), format(r.getCreatedAt()), format(s.getDeadlineAt()));
    }

    private LiveRound liveRound(SimulationSession s, SimulationRound r) {
        var list = messages.findByRoundId(r.getId()).stream()
                .sorted(Comparator.comparing(m -> m.getSpeakerRole().ordinal()))
                .map(m -> message(s, m, r.getRoundNo())).toList();
        return new LiveRound(r.getRoundNo(), r.getPhase().name(), list.size() == 2, r.getPhase() == RoundPhase.COMPLETE, list,
                analysis(r), format(r.getReadingUntil()));
    }

    public static Outcome outcome(SimulationSession s) {
        if (s.getStatus() != SimulationStatus.FINISHED) return null;
        return new Outcome(s.getOutcomeCode().name(), s.getOutcomeCode().displayText(), s.getOutcomeReason(),
                s.getLimitReason() == null ? null : s.getLimitReason().name());
    }

    public static Interruption interruption(SimulationSession s) {
        return s.getStatus() == SimulationStatus.INTERRUPTED
                ? new Interruption(s.getInterruptionCode().name(), s.getInterruptionDetail()) : null;
    }
}
