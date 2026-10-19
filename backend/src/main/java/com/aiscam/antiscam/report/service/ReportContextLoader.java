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

    public ReportContextLoader(SimulationSessionRepository sessions, SimulationRoundRepository rounds,
            SimulationMessageRepository messages, AgentPromptRepository prompts, ConfigurationSnapshots snapshots) {
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
        return new ReportContext(prompt.getTemplateContent(), ReportContextAssembler.variables(session, savedRounds, byRound, snapshots),
                snapshots.read(session.getScammerSnapshot()).path("name").asText(), savedRounds.size(), session.getOutcomeCode().displayText());
    }
}
