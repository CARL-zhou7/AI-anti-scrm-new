package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentCallException.Category;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.aiscam.antiscam.simulation.domain.FundAction;
import com.aiscam.antiscam.simulation.domain.FundFlowType;
import com.aiscam.antiscam.simulation.domain.FundPurpose;
import com.aiscam.antiscam.simulation.rules.FactCode;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.domain.WaitingItemType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict JSON parsing: missing required fields, extra fields and wrong types are all retryable output failures. */
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
            var fields = new HashSet<>(Set.of("summary", "roundAnalyses", "speechAnalysis", "victimBehaviorAnalysis", "outcomeAnalysis"));
            if (n.has("contentSchemaVersion")) {
                if (!n.get("contentSchemaVersion").isIntegralNumber() || !n.get("contentSchemaVersion").canConvertToInt() || n.get("contentSchemaVersion").intValue() != 1) throw fail(Category.REPORT, "wrong report version");
                fields.add("contentSchemaVersion");
            }
            exactFields(n, fields, Category.REPORT);
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

    public static ReportContentV2 reportV2(String raw) { return (ReportContentV2) reportStructured(raw,2); }
    public static ReportContentV3 reportV3(String raw) { return (ReportContentV3) reportStructured(raw,3); }

    private static ReportData reportStructured(String raw, int version) {
        var c = Category.REPORT;
        try {
            JsonNode n = MAPPER.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(raw);
            if (n == null || !n.isObject()) throw fail(c, "object expected");
            exactFields(n, Set.of("contentSchemaVersion", "result", "summary", "keyNodes", "preventionAdvice", "fundsSummary"), c);
            if (reportInteger(n, "contentSchemaVersion") != version) throw fail(c, "wrong report version");
            var result = n.get("result");
            exactFields(result, Set.of("text", "reason"), c);
            var funds = n.get("fundsSummary");
            exactFields(funds, version == 2 ? Set.of("totalPaidYuan", "totalRefundedYuan", "actualLossYuan") : Set.of(
                    "totalPaidYuan", "totalRefundedYuan", "totalRewardedYuan", "actualLossYuan", "availableBalanceYuan",
                    "generalReserveRemainingYuan", "emergencyReserveRemainingYuan"), c);
            if (!n.get("keyNodes").isArray() || !n.get("preventionAdvice").isArray()) throw fail(c, "arrays expected");
            var nodes = new ArrayList<ReportKeyNode>();
            for (JsonNode item : n.get("keyNodes")) {
                exactFields(item, item.has("title") ? Set.of("roundNo", "quote", "analysis", "title") : Set.of("roundNo", "quote", "analysis"), c);
                nodes.add(new ReportKeyNode(reportInteger(item, "roundNo"), nullableText(item,"quote",c),text(item,"analysis",c), item.hasNonNull("title") ? text(item,"title",c) : null));
            }
            var advice = new ArrayList<String>();
            for (JsonNode item : n.get("preventionAdvice")) {
                if (!item.isTextual()) throw fail(c, "advice must be text");
                advice.add(item.asText());
            }
            if (version == 3) return new ReportContentV3(3, new ReportResult(text(result,"text",c),text(result,"reason",c)),
                    text(n,"summary",c), List.copyOf(nodes), List.copyOf(advice), new ReportFundsSummaryV3(
                    reportInteger(funds,"totalPaidYuan"),reportInteger(funds,"totalRefundedYuan"),reportInteger(funds,"totalRewardedYuan"),
                    reportInteger(funds,"actualLossYuan"),reportInteger(funds,"availableBalanceYuan"),
                    reportInteger(funds,"generalReserveRemainingYuan"),reportInteger(funds,"emergencyReserveRemainingYuan")));
            return new ReportContentV2(2, new ReportResult(text(result,"text",c),text(result,"reason",c)),
                    text(n,"summary",c), List.copyOf(nodes), List.copyOf(advice), new ReportFundsSummary(
                    reportInteger(funds,"totalPaidYuan"),reportInteger(funds,"totalRefundedYuan"),reportInteger(funds,"actualLossYuan")));
        } catch (AgentCallException e) { throw new ReportValidationException(e.getMessage()); }
        catch (java.io.IOException | IllegalArgumentException e) { throw new ReportValidationException("invalid report JSON"); }
    }

    private static int reportInteger(JsonNode n, String field) {
        var v = n.get(field);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToInt()) throw fail(Category.REPORT,"integer expected");
        return v.intValue();
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
        // A misplaced null carries no reserve instruction; non-null values still fail the contract.
        if (n.has("reserveTier") && n.get("reserveTier").isNull()) fields.add("reserveTier");
        if (n.has("relatedPaymentId") && n.get("relatedPaymentId").isNull()) fields.add("relatedPaymentId");
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
        var fields = new HashSet<>(Set.of("type", "purpose", "amountYuan", "roundNo", "speaker", "quote", "eventOrder",
                "reason", "relatedPaymentId"));
        if (n.has("reserveTier")) fields.add("reserveTier");
        exactFields(n, fields, c);
        try {
            return new FundAction(enumValue(n,"type",FundFlowType.class,c), enumValue(n,"purpose",FundPurpose.class,c),
                    integer(n, "amountYuan"), integer(n, "roundNo"), enumValue(n,"speaker",SpeakerRole.class,c),
                    text(n, "quote", c), integer(n, "eventOrder"), nullableText(n, "reason", c),
                    n.get("relatedPaymentId").isNull() ? null : paymentId(n),
                    n.hasNonNull("reserveTier") ? enumValue(n,"reserveTier",com.aiscam.antiscam.simulation.domain.FundReserveTier.class,c) : null);
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
        JsonNode n = narratorObject(raw, c, Set.of("fraudStage", "speechAnalysis", "methodAnalysis", "victimPsychology",
                "riskScore", "riskReason", "actualFraudOccurred", "goalAchieved", "cooperationStopped", "evidence",
                "candidateOutcome", "candidateReason"));        JsonNode risk = n.get("riskScore");
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
                nullableText(n, "candidateReason", c), n.has("characterExit") && bool(n, "characterExit"),
                n.has("characterExitReason") ? nullableText(n, "characterExitReason", c) : null,
                n.has("attitudeEvents") ? trustEvents(n.get("attitudeEvents"), true, false) : List.of(),
                n.has("waitingUpdates") ? waitingUpdates(n.get("waitingUpdates")) : List.of(),
                n.has("methodShort") ? nullableText(n, "methodShort", c) : null,
                n.has("intentShort") ? nullableText(n, "intentShort", c) : null);
    }

    private static JsonNode narratorObject(String raw, Category c, Set<String> required) {
        JsonNode n;
        try { n = MAPPER.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(raw); }
        catch (java.io.IOException e) { throw fail(c, "output is not strict JSON"); }
        if (n == null || !n.isObject()) throw fail(c, "object expected");
        var fields = new HashSet<>(required);
        if (n.has("characterExit")) fields.add("characterExit");
        if (n.has("characterExitReason")) fields.add("characterExitReason");
        if (n.has("attitudeEvents")) fields.add("attitudeEvents");
        if (n.has("waitingUpdates")) fields.add("waitingUpdates");
        if (n.has("methodShort")) fields.add("methodShort");
        if (n.has("intentShort")) fields.add("intentShort");
        exactFields(n, fields, c);
        return n;
    }

    public static List<TrustEventCandidate> event(String raw) { return event(raw, false); }

    public static List<TrustEventCandidate> event(String raw, boolean trustV3) {
        try {
            JsonNode n = MAPPER.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(raw);
            exactFields(n, Set.of("events"), Category.ANALYSIS);
            return trustEvents(n.get("events"), false, trustV3);
        } catch (java.io.IOException e) { throw fail(Category.ANALYSIS, "event output is not strict JSON"); }
    }

    private static List<TrustEventCandidate> trustEvents(JsonNode array, boolean attitude, boolean trustV3) {
        var c = Category.ANALYSIS;
        if (attitude && array != null && array.isNull()) return List.of();
        if (array == null || !array.isArray()) throw fail(c, "events must be an array");
        var result = new ArrayList<TrustEventCandidate>();
        for (JsonNode item : array) {
            var fields = new HashSet<>(Set.of("type", "subjectKey", "quote", "source"));
            if (!attitude && item.has("materials")) fields.add("materials");
            if (!attitude && item.has("requirement")) fields.add("requirement");
            if (item.has("progressKey")) fields.add("progressKey");
            if (item.has("counterevidenceLevel")) fields.add("counterevidenceLevel");
            if (item.has("speaker")) fields.add("speaker");
            if (item.has("eventOrder")) fields.add("eventOrder");
            exactFields(item, fields, c);
            var allowed = attitude ? Set.of(TrustEventType.INDEPENDENT_SUPPORT,TrustEventType.INDEPENDENT_DOUBT)
                    : trustV3 ? java.util.EnumSet.allOf(TrustEventType.class)
                    : Set.of(TrustEventType.NEW_EFFECTIVE_EXPLANATION,TrustEventType.PROMISE_FULFILLED,
                            TrustEventType.NEW_CONTRADICTION,TrustEventType.DECISIVE_COUNTEREVIDENCE,TrustEventType.REPEAT_NO_PROGRESS,
                            TrustEventType.DOUBT_RESOLVED,TrustEventType.PROFESSIONAL_EXPLANATION,
                            TrustEventType.RELEVANT_MATERIAL,TrustEventType.EVASIVE_RESPONSE);
            var event = new TrustEventCandidate(eventEnum(item,"type",TrustEventType.class,allowed),
                    text(item,"subjectKey",c),text(item,"quote",c),
                    eventEnum(item,"source",EvidenceSource.class,Set.of(EvidenceSource.CONTENT,EvidenceSource.ACTION)),
                    materials(item.get("materials")),requirement(item.get("requirement")),
                    item.hasNonNull("progressKey") ? text(item,"progressKey",c) : null,
                    item.hasNonNull("counterevidenceLevel")
                            ? eventEnum(item,"counterevidenceLevel",
                                    com.aiscam.antiscam.simulation.rules.CounterevidenceLevel.class,
                                    java.util.EnumSet.allOf(com.aiscam.antiscam.simulation.rules.CounterevidenceLevel.class))
                            : null,
                    item.hasNonNull("speaker") ? eventEnum(item,"speaker",SpeakerRole.class,
                            Set.of(SpeakerRole.SCAMMER,SpeakerRole.VICTIM)) : null,
                    item.hasNonNull("eventOrder") ? eventInteger(item, "eventOrder") : null);
            AgentOutputValidator.trustEvent(event,attitude,trustV3);
            result.add(event);
        }
        return List.copyOf(result);
    }

    private static int eventInteger(JsonNode n, String field) {
        var value = n.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt())
            throw fail(Category.ANALYSIS, field + " must be an integer");
        return value.intValue();
    }

    private static <E extends Enum<E>> E eventEnum(JsonNode node, String field, Class<E> type, Set<E> allowed) {
        return enumValue(node,field,type,allowed,Category.ANALYSIS);
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type, Category category) {
        return enumValue(node,field,type,java.util.EnumSet.allOf(type),category);
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type, Set<E> allowed, Category category) {
        String value = text(node,field,category);
        try {
            E parsed = Enum.valueOf(type,value);
            if (allowed.contains(parsed)) return parsed;
        } catch (IllegalArgumentException ignored) { }
        throw fail(category,"invalid "+type.getSimpleName()+" field "+field+"; allowed values: "
                + allowed.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(",")));
    }

    private static List<Material> materials(JsonNode n) {
        if (n == null || n.isNull()) return List.of();
        if (!n.isArray() || n.size()>10) throw fail(Category.ANALYSIS,"invalid materials");
        var result = new ArrayList<Material>();
        for (var m : n) {
            var fields = new HashSet<>(Set.of("subjectKey"));
            for (String field : List.of("amountYuan","date","accountName","purpose")) if (m.has(field)) fields.add(field);
            exactFields(m,fields,Category.ANALYSIS);
            String subject = text(m,"subjectKey",Category.ANALYSIS);
            if (subject.length()>128) throw fail(Category.ANALYSIS,"invalid material subject");
            JsonNode amountNode = m.get("amountYuan");
            Integer amount = null;
            if (amountNode != null && !amountNode.isNull()) {
                if (!amountNode.isIntegralNumber() || !amountNode.canConvertToInt() || amountNode.intValue()<0)
                    throw fail(Category.ANALYSIS,"invalid material amount");
                amount = amountNode.intValue();
            }
            String date = materialText(m,"date");
            String account = materialText(m,"accountName");
            String purpose = materialText(m,"purpose");
            // Research documents and incomplete receipts remain visible, without a financial completeness claim.
            if (amount==null || amount==0 || date==null || date.isBlank()
                    || account==null || account.isBlank() || purpose==null || purpose.isBlank()) continue;
            var material = new Material(subject,amount,date,account,purpose);
            if (AgentOutputValidator.materialDetailsExplicit(material)) result.add(material);
        }
        return List.copyOf(result);
    }
    private static String materialText(JsonNode material, String field) {
        JsonNode value = material.get(field);
        if (value==null || value.isNull()) return null;
        if (!value.isTextual() || value.asText().length()>64) throw fail(Category.ANALYSIS,"invalid material text");
        return value.asText();
    }

    private static Requirement requirement(JsonNode n) {
        if (n == null || n.isNull()) return null;
        exactFields(n,Set.of("type","subjectKey","text","basis"),Category.ANALYSIS);
        return new Requirement(eventEnum(n,"type",WaitingItemType.class,Set.of(WaitingItemType.MATERIAL,WaitingItemType.INFORMATION)),
                text(n,"subjectKey",Category.ANALYSIS),text(n,"text",Category.ANALYSIS),text(n,"basis",Category.ANALYSIS));
    }
    private static List<WaitingUpdate> waitingUpdates(JsonNode n) {
        if (n == null || !n.isArray() || n.size()>10) throw fail(Category.ANALYSIS,"invalid waiting updates");
        var result = new ArrayList<WaitingUpdate>();
        for (var u : n) {
            exactFields(u,Set.of("action","type","subjectKey","text","basis"),Category.ANALYSIS);
            try {
                var update=new WaitingUpdate(enumValue(u,"action",WaitingAction.class,Category.ANALYSIS),
                        enumValue(u,"type",com.aiscam.antiscam.simulation.domain.WaitingItemType.class,Category.ANALYSIS),
                        text(u,"subjectKey",Category.ANALYSIS),text(u,"text",Category.ANALYSIS),text(u,"basis",Category.ANALYSIS));
                AgentOutputValidator.waitingUpdate(update); result.add(update);
            } catch (IllegalArgumentException e) { throw fail(Category.ANALYSIS,"invalid waiting update"); }
        }
        return List.copyOf(result);
    }

    private static EvidenceItem evidence(JsonNode e) {
        var c = Category.ANALYSIS;
        if (!e.isObject()) throw fail(c, "evidence item must be an object");
        exactFields(e, Set.of("factCode", "speaker", "source", "roundNo", "quote", "eventOrder"), c);
        try {
            if (!e.get("roundNo").isIntegralNumber() || !e.get("eventOrder").isIntegralNumber()) throw fail(c, "numbers expected");
            return new EvidenceItem(enumValue(e,"factCode",FactCode.class,c), enumValue(e,"speaker",SpeakerRole.class,c),
                    enumValue(e,"source",EvidenceSource.class,c), e.get("roundNo").asInt(), text(e, "quote", c),
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
            return new ToneHint(enumValue(n,"speed",Speed.class,c), n.get("style").isTextual() ? n.get("style").asText() : "");
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
        if (n == null || !n.isObject()) throw fail(c,"object expected");
        Set<String> names = new HashSet<>();
        n.fieldNames().forEachRemaining(names::add);
        if (!names.equals(fields)) {
            var missing = new java.util.TreeSet<>(fields); missing.removeAll(names);
            var extra = new java.util.TreeSet<>(names); extra.removeAll(fields);
            throw fail(c, "unexpected field set; expected fields: "
                    + fields.stream().sorted().collect(java.util.stream.Collectors.joining(","))
                    + "; missing fields: " + String.join(",", missing) + "; extra fields: " + String.join(",", extra));
        }
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
