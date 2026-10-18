package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentCallException.Category;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.aiscam.antiscam.simulation.rules.FactCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict JSON parsing: missing fields, extra fields and wrong types are all retryable output failures. */
public final class AgentOutputParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private AgentOutputParser() {}

    public static ScammerTurn scammer(String raw) {
        JsonNode n = object(raw, Category.DIALOGUE, Set.of("line", "action", "intent", "tone"));
        return new ScammerTurn(text(n, "line", Category.DIALOGUE), nullableText(n, "action", Category.DIALOGUE),
                text(n, "intent", Category.DIALOGUE), tone(n.get("tone")));
    }

    public static VictimTurn victim(String raw) {
        JsonNode n = object(raw, Category.DIALOGUE, Set.of("line", "action", "psychology", "tone"));
        return new VictimTurn(text(n, "line", Category.DIALOGUE), nullableText(n, "action", Category.DIALOGUE),
                text(n, "psychology", Category.DIALOGUE), tone(n.get("tone")));
    }

    public static NarratorJudgement narrator(String raw) {
        var c = Category.ANALYSIS;
        JsonNode n = object(raw, c, Set.of("fraudStage", "speechAnalysis", "methodAnalysis", "victimPsychology",
                "riskScore", "riskReason", "actualFraudOccurred", "goalAchieved", "cooperationStopped", "evidence",
                "candidateOutcome", "candidateReason"));
        JsonNode risk = n.get("riskScore");
        if (!risk.isIntegralNumber() || !risk.canConvertToInt()) throw fail(c, "riskScore must be an integer");
        List<EvidenceItem> evidence = new ArrayList<>();
        if (!n.get("evidence").isArray()) throw fail(c, "evidence must be an array");
        for (JsonNode e : n.get("evidence")) evidence.add(evidence(e));
        String candidate = nullableText(n, "candidateOutcome", c);
        OutcomeCode outcome = null;
        if (candidate != null) {
            try { outcome = OutcomeCode.valueOf(candidate); } catch (IllegalArgumentException ex) { throw fail(c, "unknown outcome"); }
        }
        return new NarratorJudgement(text(n, "fraudStage", c), text(n, "speechAnalysis", c), text(n, "methodAnalysis", c),
                text(n, "victimPsychology", c), risk.asInt(), text(n, "riskReason", c), bool(n, "actualFraudOccurred"),
                bool(n, "goalAchieved"), bool(n, "cooperationStopped"), List.copyOf(evidence), outcome,
                nullableText(n, "candidateReason", c));
    }

    private static EvidenceItem evidence(JsonNode e) {
        var c = Category.ANALYSIS;
        if (!e.isObject()) throw fail(c, "evidence item must be an object");
        exactFields(e, Set.of("factCode", "speaker", "source", "roundNo", "quote", "eventOrder"), c);
        try {
            if (!e.get("roundNo").isIntegralNumber() || !e.get("eventOrder").isIntegralNumber()) throw fail(c, "numbers expected");
            return new EvidenceItem(FactCode.valueOf(text(e, "factCode", c)), SpeakerRole.valueOf(text(e, "speaker", c)),
                    EvidenceSource.valueOf(text(e, "source", c)), e.get("roundNo").asInt(), text(e, "quote", c),
                    e.get("eventOrder").asInt());
        } catch (IllegalArgumentException ex) {
            throw fail(c, "invalid evidence enum");
        }
    }

    private static ToneHint tone(JsonNode n) {
        var c = Category.DIALOGUE;
        if (n == null || !n.isObject()) throw fail(c, "tone must be an object");
        exactFields(n, Set.of("speed", "style"), c);
        try {
            return new ToneHint(Speed.valueOf(text(n, "speed", c)), n.get("style").isTextual() ? n.get("style").asText() : "");
        } catch (IllegalArgumentException ex) {
            throw fail(c, "invalid tone speed");
        }
    }

    private static JsonNode object(String raw, Category c, Set<String> fields) {
        JsonNode n;
        try { n = MAPPER.readTree(raw); } catch (Exception e) { throw fail(c, "output is not JSON"); }
        if (n == null || !n.isObject()) throw fail(c, "output must be a JSON object");
        exactFields(n, fields, c);
        return n;
    }

    private static void exactFields(JsonNode n, Set<String> fields, Category c) {
        Set<String> names = new HashSet<>();
        n.fieldNames().forEachRemaining(names::add);
        if (!names.equals(fields)) throw fail(c, "unexpected field set");
    }

    private static String text(JsonNode n, String field, Category c) {
        JsonNode v = n.get(field);
        if (v == null || !v.isTextual()) throw fail(c, field + " must be a string");
        return v.asText();
    }

    private static String nullableText(JsonNode n, String field, Category c) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) throw fail(c, field + " must be a string or null");
        return v.asText();
    }

    private static boolean bool(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.isBoolean()) throw fail(Category.ANALYSIS, field + " must be boolean");
        return v.asBoolean();
    }

    static AgentCallException fail(Category c, String message) { return new AgentCallException(c, true, message); }
}
