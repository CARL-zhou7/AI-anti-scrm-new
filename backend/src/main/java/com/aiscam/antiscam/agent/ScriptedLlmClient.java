package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentCallException.Category;
import com.aiscam.antiscam.agent.AgentModels.AgentRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev/test only (refused outside the dev/test profiles). Returns fixed outputs from
 * {@code classpath:simulation/scripted/<script>.json}. The script is chosen by the session's scammer profile name:
 * a name of the form {@code 剧本:<script>} selects {@code <script>.json}; any other name uses {@code default.json}.
 * <p>
 * Script layout: {@code {"delayMs":n, "rounds":{"1":{"scammer":[...],"victim":[...],"narrator":[...]}},
 * "defaults":{...}}}. Each role holds one entry per call attempt (the last entry repeats): either
 * {@code {"output":{...}}} or {@code {"error":"EMPTY"|"TIMEOUT"}}, optionally with {@code "delayMs"}.
 * Text values may contain {@code {{round}}}, replaced by the round number.
 */
public class ScriptedLlmClient implements LlmClient {
    private final ObjectMapper mapper;
    private final Map<String, JsonNode> scripts = new ConcurrentHashMap<>();

    public ScriptedLlmClient(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public String completeJson(LlmRequest request) {
        Category category = switch (request.role()) {
            case NARRATOR -> Category.ANALYSIS;
            case REPORT -> Category.REPORT;
            default -> Category.DIALOGUE;
        };
        JsonNode script = script(scriptName(request.scriptKey()));
        String role = request.role().name().toLowerCase(Locale.ROOT);
        JsonNode attempts = attempts(script, request.roundNo(), role);
        if (attempts == null) throw new AgentCallException(category, false, "no scripted output");
        JsonNode entry = attempts.get(Math.min(request.attempt(), attempts.size() - 1));
        sleep(entry.path("delayMs").asLong(script.path("delayMs").asLong(0)));
        if (entry.has("error")) throw new AgentCallException(category, true, "scripted " + entry.path("error").asText());
        try {
            return mapper.writeValueAsString(entry.path("output")).replace("{{round}}", Integer.toString(request.roundNo()));
        } catch (IOException e) {
            throw new AgentCallException(category, false, "invalid script");
        }
    }

    /** Round entry, then the script's defaults, then (via {@code "extends"}) the base script's. */
    private JsonNode attempts(JsonNode script, int roundNo, String role) {
        for (JsonNode s = script; s != null; s = s.has("extends") ? script(s.path("extends").asText()) : null) {
            JsonNode a = s.path("rounds").path(Integer.toString(roundNo)).path(role);
            if (a.isArray() && !a.isEmpty()) return a;
            a = s.path("defaults").path(role);
            if (a.isArray() && !a.isEmpty()) return a;
        }
        return null;
    }

    static String scriptName(String scammerName) {
        if (scammerName != null && scammerName.startsWith("剧本:")) {
            String name = scammerName.substring(3).trim();
            if (name.matches("[a-z0-9-]{1,40}")) return name;
        }
        return "default";
    }

    private JsonNode script(String name) {
        String resolved = ScriptedLlmClient.class.getResource("/simulation/scripted/" + name + ".json") != null ? name : "default";
        return scripts.computeIfAbsent(resolved, key -> {
            try (InputStream in = ScriptedLlmClient.class.getResourceAsStream("/simulation/scripted/" + key + ".json")) {
                if (in == null) return mapper.createObjectNode();
                return mapper.readTree(in);
            } catch (IOException e) {
                throw new IllegalStateException("Unreadable script " + key, e);
            }
        });
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
