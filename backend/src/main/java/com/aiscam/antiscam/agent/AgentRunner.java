package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.agent.LlmClient.LlmRequest;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.report.service.ReportContext;
import java.time.Duration;
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

    public ScammerTurn scammer(AgentScene scene, List<DialogueLine> history, int roundNo, long remainingMs, int attempt) {
        String prompt = PromptRenderer.render(scene.scammerTemplate(),
                AgentContextAssembler.scammer(scene, history, roundNo, remainingMs / 1000));
        String raw = llm.completeJson(request(AgentRole.SCAMMER, prompt, config.getScammerTemperature(), remainingMs, roundNo, attempt, scene));
        return AgentOutputValidator.validate(AgentOutputParser.scammer(raw));
    }

    public VictimTurn victim(AgentScene scene, List<DialogueLine> history, DialogueLine scammerLine, int roundNo,
            long remainingMs, boolean regeneration, int attempt) {
        String prompt = PromptRenderer.render(scene.victimTemplate(),
                AgentContextAssembler.victim(scene, history, scammerLine, roundNo, remainingMs / 1000, regeneration));
        String raw = llm.completeJson(request(AgentRole.VICTIM, prompt, config.getVictimTemperature(), remainingMs, roundNo, attempt, scene));
        return AgentOutputValidator.validate(AgentOutputParser.victim(raw));
    }

    public ValidatedJudgement narrator(AgentScene scene, List<DialogueLine> lines, int roundNo, PriorFacts prior,
            long remainingMs, int attempt) {
        String prompt = PromptRenderer.render(scene.narratorTemplate(), AgentContextAssembler.narrator(scene, lines, roundNo, prior));
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
