package com.aiscam.antiscam.simulation.service;

import static com.aiscam.antiscam.common.time.UtcTime.format;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.report.service.ReportViewMapper;
import com.aiscam.antiscam.report.rules.ReportEligibility;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.*;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** The only place a SimulationState is built; HTTP responses and SSE frames share it. Call inside a transaction. */
@Component
public class SimulationStateAssembler {
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final SimulationReportRepository reports;
    private final ConfigurationSnapshots snapshots;
    private final TempAudioStore audio;
    private final SimulationTrustEventRepository trustEvents;
    private final InterventionStateAssembler interventions;

    public SimulationStateAssembler(SimulationRoundRepository rounds, SimulationMessageRepository messages,
            SimulationReportRepository reports, ConfigurationSnapshots snapshots, TempAudioStore audio,
            SimulationTrustEventRepository trustEvents) {
        this(rounds, messages, reports, snapshots, audio, trustEvents, null);
    }

    @Autowired
    public SimulationStateAssembler(SimulationRoundRepository rounds, SimulationMessageRepository messages,
            SimulationReportRepository reports, ConfigurationSnapshots snapshots, TempAudioStore audio,
            SimulationTrustEventRepository trustEvents, InterventionStateAssembler interventions) {
        this.interventions = interventions;
        this.rounds = rounds;
        this.messages = messages;
        this.reports = reports;
        this.snapshots = snapshots;
        this.audio = audio;
        this.trustEvents = trustEvents;
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
                s.getPausedTotalMs(), report, Funds.from(s, false, snapshots.fundsV3(s)), s.getWinner(), s.getFinalTrustScore(),
                s.isAdminTest() && interventions != null ? interventions.assemble(s, interventions.currentDraft(s)) : null,
                snapshots.sceneRuleCount(s.getSceneRuleSnapshot()));
    }

    public SessionStateData sessionState(SimulationSession s) {
        return new SessionStateData(assemble(s), reportView(s));
    }

    public boolean reportEligible(SimulationSession s) {
        // An old session whose own rules never produced a report is ineligible, but a report row that really exists
        // must stay readable: new eligibility rules never hide a saved legacy report.
        return ReportEligibility.isEligible(s, rounds.countBySessionIdAndPhase(s.getId(),RoundPhase.COMPLETE))
                || (s.getStatus() != SimulationStatus.RUNNING && reports.existsById(s.getId()));
    }

    public static Ending ending(SimulationSession s) {
        if (!ReportEligibility.isBusinessEnd(s) || !ReportEligibility.supportsBusinessReport(s)) return null;
        String display=switch(s.getInterruptionCode()) {
            case USER_EXIT -> "用户离开"; case FUNDS_EXHAUSTED -> "资金不足结束"; case CHARACTER_EXIT -> "角色离开";
            default -> throw new IllegalStateException("Not a business ending");
        };
        return new Ending(s.getInterruptionCode().name(),display,ReportEligibility.endingReason(s));
    }

    public ReportView reportView(SimulationSession s) {
        boolean eligible=reportEligible(s);
        List<RiskPoint> series = eligible
                ? rounds.findBySessionIdOrderByRoundNoAsc(s.getId()).stream().filter(SimulationRound::hasAnalysis)
                        .map(r -> new RiskPoint(r.getRoundNo(), r.getRiskScore(), trustChange(s, r.getRoundNo()))).toList()
                : List.of();
        String unavailable = eligible ? null : s.getStatus()==SimulationStatus.RUNNING ? "SIMULATION_RUNNING"
                : ReportEligibility.isLegacyExit(s) ? "LEGACY_NO_REPORT"
                : ReportEligibility.isBusinessEnd(s) ? "NO_DIALOGUE" : "TECHNICAL_INTERRUPT";
        if (unavailable != null) return new ReportView(s.getId().toString(), s.getStatus().name(), null, unavailable,
                null, null, series, null, ending(s));
        var row = reports.findById(s.getId()).orElseThrow(() -> ApiException.dataInconsistent("记录状态异常，请稍后重试"));
        var report = ReportViewMapper.summary(row);
        com.aiscam.antiscam.agent.AgentModels.ReportData content = null;
        if (row.getStatus() == ReportStatus.SUCCEEDED) {
            if (row.getContentSchemaVersion() >= 2) {
                var savedRounds = rounds.findBySessionIdOrderByRoundNoAsc(s.getId());
                var byRound = new java.util.HashMap<Long,List<SimulationMessage>>();
                for (var round : savedRounds) byRound.put(round.getId(),messages.findByRoundId(round.getId()));
                if (row.getContentSchemaVersion() == 4) content = ReportViewMapper.contentV4(row.getContentJson(),
                        s.getOutcomeCode() == null ? null : com.aiscam.antiscam.report.service.ReportContextAssembler.outcomeText(s,savedRounds),
                        com.aiscam.antiscam.agent.AgentModels.ReportFundsSummaryV3.from(s),
                        com.aiscam.antiscam.report.service.ReportContextAssembler.quotes(savedRounds,byRound),
                        s.getWinner(), s.getFinalTrustScore());
                else if (row.getContentSchemaVersion() == 3) content = ReportViewMapper.contentV3(row.getContentJson(),
                        s.getOutcomeCode() == null ? null : com.aiscam.antiscam.report.service.ReportContextAssembler.outcomeText(s,savedRounds),
                        com.aiscam.antiscam.agent.AgentModels.ReportFundsSummaryV3.from(s),
                        com.aiscam.antiscam.report.service.ReportContextAssembler.quotes(savedRounds,byRound));
                else content = ReportViewMapper.contentV2(row.getContentJson(),s.getOutcomeCode().displayText(),
                        com.aiscam.antiscam.agent.AgentModels.ReportFundsSummary.from(s),
                        com.aiscam.antiscam.report.service.ReportContextAssembler.quotes(savedRounds,byRound));
            } else {
                content = ReportViewMapper.content(row.getContentJson(),rounds.findBySessionIdOrderByRoundNoAsc(s.getId()).size(),s.getOutcomeCode().displayText());
            }
        }
        return new ReportView(s.getId().toString(), s.getStatus().name(), report, null, outcome(s), content, series, content == null ? null : row.getContentSchemaVersion(), ending(s));
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

    public Analysis analysis(SimulationSession s, SimulationRound r) {
        if (!r.hasAnalysis()) return null;
        return new Analysis(r.getFraudStage(), r.getSpeechAnalysis(), r.getMethodAnalysis(), r.getVictimPsychology(),
                r.getRiskScore(), r.getRiskReason(), format(r.getAnalysisSavedAt()), format(r.getAnalysisPresentedAt()),
                trustChange(s, r.getRoundNo()), r.getMethodShort(), r.getIntentShort());
    }

    private TrustChange trustChange(SimulationSession s, int roundNo) {
        if (s.getInitialTrustScore() == null) return null;
        int before = s.getInitialTrustScore();
        int after = before;
        for (var event : trustEvents.findBySessionIdOrderByIdAsc(s.getId())) {
            if (event.getRoundNo() > roundNo) continue;
            if (event.getRoundNo() < roundNo) before = event.getScoreAfter();
            after = event.getScoreAfter();
        }
        return new TrustChange(before, after, after - before);
    }

    public RoundStarted roundStarted(SimulationSession s, SimulationRound r) {
        return new RoundStarted(r.getRoundNo(), r.getPhase().name(), format(r.getCreatedAt()), format(s.getDeadlineAt()));
    }

    private LiveRound liveRound(SimulationSession s, SimulationRound r) {
        var list = messages.findByRoundId(r.getId()).stream()
                .sorted(Comparator.comparing(m -> m.getSpeakerRole().ordinal()))
                .map(m -> message(s, m, r.getRoundNo())).toList();
        return new LiveRound(r.getRoundNo(), r.getPhase().name(), list.size() == 2, r.getPhase() == RoundPhase.COMPLETE, list,
                analysis(s, r), format(r.getReadingUntil()));
    }

    public Outcome outcome(SimulationSession s) {
        if (s.getStatus() != SimulationStatus.FINISHED) return null;
        boolean v2 = ConfigurationSnapshots.trustV2(s);
        var round = s.getOutcomeCode().isV12() || v2 ? rounds.findBySessionIdAndRoundNo(s.getId(), s.getCurrentRoundNo()).orElse(null) : null;
        String display = v2 ? com.aiscam.antiscam.simulation.rules.OutcomeRules.displayV2(s.getOutcomeCode(),
                round != null && round.isGoalAchieved(), round != null && round.isActualFraudOccurred() && round.isCooperationStopped())
                : s.getOutcomeCode().displayText();
        return new Outcome(s.getOutcomeCode().name(), display, ReportViewMapper.text(s.getOutcomeReason()),
                s.getLimitReason() == null ? null : s.getLimitReason().name(),
                round == null ? null : round.isGoalAchieved(),
                round == null ? null : round.isActualFraudOccurred() && round.isCooperationStopped(),
                s.getOutcomeCode().isV12() || v2 ? s.getActualLossYuan() : null,
                s.getWinner(), s.getFinalTrustScore());
    }

    public static Interruption interruption(SimulationSession s) {
        return s.getStatus() == SimulationStatus.INTERRUPTED
                ? new Interruption(s.getInterruptionCode().name(), s.getInterruptionDetail()) : null;
    }
}
