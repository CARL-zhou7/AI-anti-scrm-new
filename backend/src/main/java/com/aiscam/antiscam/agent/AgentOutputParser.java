package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentCallException.Category;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.aiscam.antiscam.simulation.domain.FundAction;
import com.aiscam.antiscam.simulation.domain.FundFlowType;
import com.aiscam.antiscam.simulation.domain.FundPurpose;
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

    public static ReportContent report(String raw) {
        try {
            // Report output must be exactly one JSON object, including duplicate/trailing-token checks.
            JsonNode n = MAPPER.reader()
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(raw);
            if (n == null || !n.isObject()) throw fail(Category.REPORT, "object expected");
            exactFields(n, Set.of("summary", "roundAnalyses", "speechAnalysis", "victimBehaviorAnalysis", "outcomeAnalysis"), Category.REPORT);
            if (!n.get("roundAnalyses").isArray()) throw fail(Category.REPORT, "roundAnalyses must be an array");
            List<RoundAnalysis> rounds = new ArrayList<>();
            for (JsonNode r : n.get("roundAnalyses")) {
                if (!r.isObject()) throw fail(Category.REPORT, "round analysis must be an object");
                exactFields(r, Set.of("roundNo", "analysis"), Category.REPORT);
                if (!r.get("roundNo").isIntegralNumber() || !r.get("roundNo").canConvertToInt()) {
                    throw fail(Category.REPORT, "roundNo must be an integer");
                }
                rounds.add(new RoundAnalysis(r.get("roundNo").asInt(), text(r, "analysis", Category.REPORT)));
            }
            return new ReportContent(text(n, "summary", Category.REPORT), List.copyOf(rounds),
                    text(n, "speechAnalysis", Category.REPORT), text(n, "victimBehaviorAnalysis", Category.REPORT),
                    text(n, "outcomeAnalysis", Category.REPORT));
        } catch (AgentCallException e) {
            throw new ReportValidationException(e.getMessage());
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new ReportValidationException("output is not strict JSON");
        }
    }

    public static ScammerTurn scammer(String raw) {
        JsonNode n = roleObject(raw, "intent", true);
        return new ScammerTurn(text(n, "line", Category.DIALOGUE), nullableText(n, "action", Category.DIALOGUE),
                text(n, "intent", Category.DIALOGUE), tone(n.get("tone")),
                n.hasNonNull("claimedRefundYuan") ? integer(n, "claimedRefundYuan") : null, fundActions(n));
    }

    public static VictimTurn victim(String raw) {
        JsonNode n = roleObject(raw, "psychology", false);
        return new VictimTurn(text(n, "line", Category.DIALOGUE), nullableText(n, "action", Category.DIALOGUE),
                text(n, "psychology", Category.DIALOGUE), tone(n.get("tone")), fundActions(n));
    }

    private static JsonNode roleObject(String raw, String note, boolean scammer) {
        JsonNode n;
        try {
            n = MAPPER.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(raw);
        } catch (java.io.IOException e) { throw fail(Category.DIALOGUE, "output is not strict JSON"); }
        if (n == null || !n.isObject()) throw fail(Category.DIALOGUE, "object expected");
        var fields = new HashSet<>(Set.of("line", "action", note, "tone"));
        if (n.has("fundActions")) fields.add("fundActions");
        if (scammer && n.has("claimedRefundYuan")) fields.add("claimedRefundYuan");
        exactFields(n, fields, Category.DIALOGUE);
        return n;
    }

    private static List<FundAction> fundActions(JsonNode n) {
        List<FundAction> actions = new ArrayList<>();
        if (n.has("fundActions")) {
            if (!n.get("fundActions").isArray()) throw fail(Category.DIALOGUE, "fundActions must be an array");
            for (JsonNode a : n.get("fundActions")) actions.add(fundAction(a));
        }
        return List.copyOf(actions);
    }

    private static FundAction fundAction(JsonNode n) {
        var c = Category.DIALOGUE;
        if (!n.isObject()) throw fail(c, "fund action must be an object");
        exactFields(n, Set.of("type", "purpose", "amountYuan", "roundNo", "speaker", "quote", "eventOrder",
                "reason", "relatedPaymentId"), c);
        try {
            return new FundAction(FundFlowType.valueOf(text(n, "type", c)), FundPurpose.valueOf(text(n, "purpose", c)),
                    integer(n, "amountYuan"), integer(n, "roundNo"), SpeakerRole.valueOf(text(n, "speaker", c)),
                    text(n, "quote", c), integer(n, "eventOrder"), nullableText(n, "reason", c),
                    n.get("relatedPaymentId").isNull() ? null : paymentId(n));
        } catch (IllegalArgumentException e) { throw fail(c, "invalid fund action enum"); }
    }

    private static long paymentId(JsonNode n) {
        var value = n.get("relatedPaymentId");
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw fail(Category.DIALOGUE, "invalid payment id");
        return value.longValue();
    }

    private static int integer(JsonNode n, String field) {
        var value = n.get(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw fail(Category.DIALOGUE, field + " must be an integer");
        return value.intValue();
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
