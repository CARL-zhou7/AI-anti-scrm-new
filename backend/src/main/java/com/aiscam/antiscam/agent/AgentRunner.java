package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.agent.LlmClient.LlmRequest;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.report.service.ReportContext;
import java.time.Duration;
import com.aiscam.antiscam.simulation.domain.*;
import java.util.List;

/**
 * Assemble context → render the pinned prompt → call the model → parse → validate. One attempt per call; retry
 * and regeneration policy belongs to the preparation area of the orchestrator.
 */
public class AgentRunner {
    private final LlmClient llm;
    private final SimulationProperties.Llm config;

    public AgentRunner(LlmClient llm, SimulationProperties.Llm config) {
        this.llm = llm;
        this.config = config;
    }

    public ReportContent report(ReportContext context, long timeoutMs) {
        long timeout = Math.max(1, Math.min(timeoutMs, config.getCallTimeoutMs()));
        String prompt = PromptRenderer.render(context.template(), context.variables());
        String raw = llm.completeJson(new LlmRequest(AgentRole.REPORT, prompt, config.getReportTemperature(),
                config.getMaxTokens(), Duration.ofMillis(timeout), 0, 1, context.scriptKey()));
        return AgentOutputValidator.report(AgentOutputParser.report(raw), context.roundCount(), context.outcomeDisplayText());
    }

    public ScammerTurn scammer(AgentScene scene, List<DialogueLine> history, int roundNo, long remainingMs, int attempt, FundsContext funds) {
        var variables = AgentContextAssembler.scammer(scene, history, roundNo, remainingMs / 1000);
        if (funds != null) {
            variables.put("funds_context", AgentContextAssembler.scammerFunds(funds));
            variables.put("victim_public_profile", scene.victimPublicProfile());
        }
        String prompt = PromptRenderer.render(scene.scammerTemplate(), variables);
        String raw = llm.completeJson(request(AgentRole.SCAMMER, prompt, config.getScammerTemperature(), remainingMs, roundNo, attempt, scene));
        var turn = AgentOutputValidator.validate(AgentOutputParser.scammer(raw));
        AgentOutputValidator.funds(SpeakerRole.SCAMMER, turn.line(), turn.action(), turn.claimedRefundYuan(), turn.fundActions(), roundNo, funds, history);
        return turn;
    }

    public VictimTurn victim(AgentScene scene, List<DialogueLine> history, DialogueLine scammerLine, int roundNo,
            long remainingMs, boolean regeneration, int attempt, FundsContext funds) {
        var variables = AgentContextAssembler.victim(scene, history, scammerLine, roundNo, remainingMs / 1000, regeneration);
        if (funds != null) variables.put("funds_context", AgentContextAssembler.victimFunds(funds));
        String prompt = PromptRenderer.render(scene.victimTemplate(), variables);
        String raw = llm.completeJson(request(AgentRole.VICTIM, prompt, config.getVictimTemperature(), remainingMs, roundNo, attempt, scene));
        var turn = AgentOutputValidator.validate(AgentOutputParser.victim(raw));
        AgentOutputValidator.funds(SpeakerRole.VICTIM, turn.line(), turn.action(), null, turn.fundActions(), roundNo, funds, history);
        return turn;
    }

    public ValidatedJudgement narrator(AgentScene scene, List<DialogueLine> lines, int roundNo, PriorFacts prior,
            long remainingMs, int attempt, FundsContext funds, Integer claimed, boolean candidate) {
        var variables = AgentContextAssembler.narrator(scene, lines, roundNo, prior);
        if (funds != null) variables.put("funds_context", AgentContextAssembler.narratorFunds(funds, claimed, candidate));
        String prompt = PromptRenderer.render(scene.narratorTemplate(), variables);
        String raw = llm.completeJson(request(AgentRole.NARRATOR, prompt, config.getNarratorTemperature(), remainingMs, roundNo, attempt, scene));
        return AgentOutputValidator.validate(AgentOutputParser.narrator(raw), scene.scenarioCode(), roundNo, lines, prior);
    }

    private LlmRequest request(AgentRole role, String prompt, double temperature, long remainingMs, int roundNo, int attempt,
            AgentScene scene) {
        long timeout = Math.max(1, Math.min(config.getCallTimeoutMs(), remainingMs));
        return new LlmRequest(role, prompt, temperature, config.getMaxTokens(), Duration.ofMillis(timeout), roundNo, attempt,
                scene.scriptKey());
    }
}
