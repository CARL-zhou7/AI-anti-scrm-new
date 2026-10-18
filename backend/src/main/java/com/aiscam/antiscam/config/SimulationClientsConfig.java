package com.aiscam.antiscam.config;

import com.aiscam.antiscam.agent.AgentRunner;
import com.aiscam.antiscam.agent.DeepSeekLlmClient;
import com.aiscam.antiscam.agent.LlmClient;
import com.aiscam.antiscam.agent.ScriptedLlmClient;
import com.aiscam.antiscam.media.CosyVoiceGatewayClient;
import com.aiscam.antiscam.media.SilentTtsClient;
import com.aiscam.antiscam.media.TtsClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects model and speech providers; scripted/silent are rejected outside dev/test by {@link SimulationConfig}. */
@Configuration
public class SimulationClientsConfig {
    @Bean
    LlmClient llmClient(SimulationProperties properties, ObjectMapper mapper) {
        var llm = properties.getLlm();
        return "scripted".equals(llm.getProvider()) ? new ScriptedLlmClient(mapper)
                : new DeepSeekLlmClient(llm.getBaseUrl(), llm.getModel(), llm.getApiKey(), mapper);
    }

    @Bean
    AgentRunner agentRunner(LlmClient llm, SimulationProperties properties) {
        return new AgentRunner(llm, properties.getLlm());
    }

    @Bean
    TtsClient ttsClient(SimulationProperties properties, ObjectMapper mapper) {
        return "silent".equals(properties.getTts().getProvider()) ? new SilentTtsClient()
                : new CosyVoiceGatewayClient(properties.getTts().getGatewayUrl(), mapper);
    }
}
