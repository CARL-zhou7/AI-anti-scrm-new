package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentCallException.Category;
import com.aiscam.antiscam.agent.AgentModels.AgentRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * OpenAI-compatible Chat Completions client (DeepSeek, JSON output mode, non-streaming). Logs only role, round,
 * latency, token usage and error category; never prompts, outputs or the API key.
 */
public class DeepSeekLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmClient.class);
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DeepSeekLlmClient(String baseUrl, String model, String apiKey, ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.apiKey = apiKey;
        this.mapper = mapper;
    }

    @Override
    public String completeJson(LlmRequest request) {
        Category category = switch (request.role()) {
            case NARRATOR -> Category.ANALYSIS;
            case REPORT -> Category.REPORT;
            default -> Category.DIALOGUE;
        };
        if (apiKey == null || apiKey.isBlank()) throw new AgentCallException(category, false, "LLM API key missing");
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(request.timeout());
        var client = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
        long began = System.nanoTime();
        try {
            String body = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("model", model,
                            "messages", List.of(Map.of("role", "system", "content", "你是严格遵守格式的助手，只输出一个json对象。"),
                                    Map.of("role", "user", "content", request.prompt())),
                            "response_format", Map.of("type", "json_object"),
                            "temperature", request.temperature(),
                            "max_tokens", request.maxTokens(),
                            "stream", false))
                    .retrieve().body(String.class);
            JsonNode json = mapper.readTree(body);
            JsonNode choice = json.path("choices").path(0);
            String content = choice.path("message").path("content").asText("");
            String finish = choice.path("finish_reason").asText("");
            log.info("llm call role={} round={} attempt={} ms={} promptTokens={} completionTokens={} finish={}",
                    request.role(), request.roundNo(), request.attempt(), (System.nanoTime() - began) / 1_000_000,
                    json.path("usage").path("prompt_tokens").asInt(-1), json.path("usage").path("completion_tokens").asInt(-1), finish);
            if (content.isBlank()) throw new AgentCallException(category, true, "empty content");
            if ("length".equals(finish)) throw new AgentCallException(category, true, "output truncated");
            return content;
        } catch (AgentCallException e) {
            throw e;
        } catch (Exception e) {
            log.info("llm call failed role={} round={} attempt={} ms={} category={}", request.role(), request.roundNo(),
                    request.attempt(), (System.nanoTime() - began) / 1_000_000, e.getClass().getSimpleName());
            throw new AgentCallException(category, true, "model call failed");
        }
    }
}
