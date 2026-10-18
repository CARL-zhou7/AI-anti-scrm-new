package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.AgentRole;
import java.time.Duration;

/** Returns the raw JSON text produced by a model. Implementations must not log prompts or outputs. */
public interface LlmClient {
    String completeJson(LlmRequest request);

    /**
     * @param roundNo and {@code attempt} identify the call for the scripted dev/test client; {@code scriptKey} is the
     *                scammer profile name of the session (see {@link ScriptedLlmClient}).
     */
    record LlmRequest(AgentRole role, String prompt, double temperature, int maxTokens, Duration timeout,
            int roundNo, int attempt, String scriptKey) {}
}
