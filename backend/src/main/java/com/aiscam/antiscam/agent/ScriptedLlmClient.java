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
            case NARRATOR, EVENT -> Category.ANALYSIS;
            case REPORT -> Category.REPORT;
            default -> Category.DIALOGUE;
        };
        String name = scriptName(request.scriptKey());
        if (name.equals("default") && ((request.role() == AgentRole.NARRATOR && request.prompt().contains("\"IDENTIFIED\""))
                || (request.role() == AgentRole.REPORT && request.prompt().contains("IDENTIFIED")))) name = "default-v12";
        JsonNode script = script(name);
        String role = request.role().name().toLowerCase(Locale.ROOT);
        JsonNode attempts = attempts(script, request.roundNo(), role);
        // Existing scripts have no victim-rule track; explicitly model no match unless the fixture defines one.
        if (attempts == null && request.role() == AgentRole.VICTIM_RULE) return """
                {"ruleId":null,"sameRequestRound":0,"requestSource":"CONTENT","amountYuan":0,"purpose":null,"recipient":"",
                 "previousResponse":"NONE","responseSource":"NONE","blocked":false,"blockingQuote":""}
                """;
        if (attempts == null) throw new AgentCallException(category, false, "no scripted output");
        JsonNode entry = attempts.get(Math.min(request.attempt(), attempts.size() - 1));
        if (request.role() == AgentRole.REPORT && request.prompt().contains("\"contentSchemaVersion\":2")
                && entry.has("output") && entry.path("output").path("contentSchemaVersion").asInt(1) != 2) {
            entry = attempts(script("report-v2"),0,"report").get(0);
        }
        if (request.role() == AgentRole.REPORT && request.prompt().contains("\"contentSchemaVersion\":3")
                && entry.has("output") && entry.path("output").path("contentSchemaVersion").asInt() != 3)
            entry = attempts(script("report-v3"),0,"report").get(0);
        sleep(entry.path("delayMs").asLong(script.path("delayMs").asLong(0)));
        if (entry.has("error")) throw new AgentCallException(category, true, "scripted " + entry.path("error").asText());
        try {
            String output = mapper.writeValueAsString(entry.path("output")).replace("{{round}}", Integer.toString(request.roundNo()));
            if (output.contains("{{payment_id}}")) {
                var payment = java.util.regex.Pattern.compile("付款ID=([0-9]+)").matcher(request.prompt());
                if (!payment.find()) throw new AgentCallException(category, false, "script requires an existing payment");
                output = output.replace("\"{{payment_id}}\"", payment.group(1));
            }
            if (request.role() == AgentRole.REPORT && output.contains("{{outcome}}")) {
                var outcome = java.util.regex.Pattern.compile("(?m)^(已受骗|已识破|超限结束|未形成受骗或识破结论|大额投入目标达成|受骗后及时止损|演绎结果未定|未受骗结束)[（：]([^\\r\\n]*)").matcher(request.prompt());
                if (!outcome.find()) throw new AgentCallException(category,false,"script requires saved outcome");
                output = output.replace("{{outcome}}",outcome.group(1));
                output = output.replace("\"{{ending_reason}}\"",mapper.writeValueAsString(outcome.group(2)));
                for (String field : (request.prompt().contains("\"contentSchemaVersion\":3") ? java.util.List.of("totalPaidYuan","totalRefundedYuan","totalRewardedYuan",
                        "actualLossYuan","availableBalanceYuan","generalReserveRemainingYuan","emergencyReserveRemainingYuan")
                        : java.util.List.of("totalPaidYuan","totalRefundedYuan","actualLossYuan"))) {
                    String label = switch(field) { case "totalPaidYuan" -> "累计支付"; case "totalRefundedYuan" -> "实际返款"; default -> "实际亏损"; };
                    var amount = java.util.regex.Pattern.compile("(?:"+field+"=|"+label+")([0-9]+)").matcher(request.prompt());
                    if (!amount.find()) throw new AgentCallException(category,false,"script requires saved funds");
                    output = output.replace("\"{{"+field+"}}\"",amount.group(1));
                }
            }
            return output;
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
