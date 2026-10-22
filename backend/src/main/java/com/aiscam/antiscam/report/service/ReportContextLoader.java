package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportContextLoader {
    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final AgentPromptRepository prompts;
    private final ConfigurationSnapshots snapshots;
    private final SimulationReportRepository reports;
    private final FundFlowRepository funds;

    public ReportContextLoader(SimulationSessionRepository sessions, SimulationRoundRepository rounds,
            SimulationMessageRepository messages, AgentPromptRepository prompts, ConfigurationSnapshots snapshots, SimulationReportRepository reports, FundFlowRepository funds) {
        this.reports = reports; this.funds = funds;
        this.sessions = sessions;
        this.rounds = rounds;
        this.messages = messages;
        this.prompts = prompts;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public ReportContext load(long sessionId) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("Missing session"));
        if (session.getStatus() != SimulationStatus.FINISHED) throw new IllegalStateException("Session is not finished");
        var prompt = prompts.findById(session.getReportPromptId()).orElseThrow(() -> new IllegalStateException("Missing report prompt"));
        var savedRounds = rounds.findBySessionIdOrderByRoundNoAsc(sessionId);
        var byRound = new HashMap<Long, List<SimulationMessage>>();
        for (var round : savedRounds) byRound.put(round.getId(), messages.findByRoundId(round.getId()));
        var row = reports.findById(sessionId).orElseThrow();
        int version = row.getContentSchemaVersion();
        if (version != (prompt.getVersionNo() >= 2 ? 2 : 1)) throw new com.aiscam.antiscam.agent.ReportValidationException("report prompt/version mismatch");
        var flows = version == 2 ? funds.findBySessionIdOrderBySequenceNoAsc(sessionId) : List.<FundFlow>of();
        if (version == 2) {
            long paid = flows.stream().filter(f -> f.getFlowType() == FundFlowType.PAYMENT).mapToLong(FundFlow::getAmountYuan).sum();
            long refunded = flows.stream().filter(f -> f.getFlowType() == FundFlowType.REFUND).mapToLong(FundFlow::getAmountYuan).sum();
            if (paid != session.getTotalPaidYuan() || refunded != session.getTotalRefundedYuan()
                    || Math.max(0,paid-refunded) != session.getActualLossYuan()) throw new com.aiscam.antiscam.agent.ReportValidationException("ledger/summary mismatch");
        }
        var variables = version == 2 ? ReportContextAssembler.variablesV2(session,savedRounds,byRound,snapshots,flows)
                : ReportContextAssembler.variables(session,savedRounds,byRound,snapshots);
        return new ReportContext(prompt.getTemplateContent(), variables,
                snapshots.read(session.getScammerSnapshot()).path("name").asText(), savedRounds.size(), session.getOutcomeCode().displayText(), version,
                version == 2 ? com.aiscam.antiscam.agent.AgentModels.ReportFundsSummary.from(session) : null,
                ReportContextAssembler.quotes(savedRounds,byRound), row.getAttemptNo());
    }
}
