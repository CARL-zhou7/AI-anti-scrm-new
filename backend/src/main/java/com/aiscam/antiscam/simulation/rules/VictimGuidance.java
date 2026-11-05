package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.agent.AgentModels.DialogueLine;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot;
import java.util.List;
import java.util.Objects;

/** A saved, request-scoped decision. The model identifies evidence; only this code grants permission. */
public record VictimGuidance(int roundNo, String ruleId, String requestId, String requestQuote,
        int amountYuan, FundPurpose purpose, String recipient, int consecutive, boolean required,
        boolean fulfilled, boolean blocked, String instruction) {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    public static VictimGuidance read(String json) {
        if (json == null) return null;
        try {
            var node = JSON.readTree(json);
            return node.has("ruleId") ? JSON.treeToValue(node, VictimGuidance.class) : null;
        } catch (java.io.IOException e) { throw new IllegalStateException("Unreadable saved victim guidance", e); }
    }

    public static Match parseRequest(String raw, DialogueLine current, List<DialogueLine> history) {
        try {
            var node = JSON.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(raw);
            require(node != null && node.isObject() && !node.has("requestQuote") && !node.has("responseQuote"),
                    "requestSource/responseSource required; do not generate requestQuote/responseQuote");
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            var source = object.remove("requestSource");
            require(source != null && source.isTextual(), "requestSource must be CONTENT or ACTION");
            String quote = switch (source.asText()) {
                case "CONTENT" -> current.content();
                case "ACTION" -> current.action();
                default -> throw new IllegalArgumentException("requestSource must be CONTENT or ACTION");
            };
            require(current.speaker() == SpeakerRole.SCAMMER && quote != null && !quote.isBlank(), "selected request source is empty");
            object.put("requestQuote", quote);
            var responseSource = object.remove("responseSource");
            require(responseSource != null && responseSource.isTextual(), "responseSource must be CONTENT, ACTION or NONE");
            String responseQuote = "";
            if ("NONE".equals(object.path("previousResponse").asText())) {
                require("NONE".equals(responseSource.asText()), "previousResponse NONE requires responseSource NONE");
            } else {
                require(object.path("sameRequestRound").isInt(), "sameRequestRound must be an integer");
                int responseRound = object.path("sameRequestRound").intValue();
                require(responseRound > 0 && responseRound < current.roundNo(), "invalid previous response round");
                var response = history.stream().filter(l -> l.roundNo() == responseRound && l.speaker() == SpeakerRole.VICTIM)
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("previous victim response missing"));
                responseQuote = switch (responseSource.asText()) {
                    case "CONTENT" -> response.content();
                    case "ACTION" -> response.action();
                    default -> throw new IllegalArgumentException("responseSource must be CONTENT or ACTION for a previous response");
                };
                require(responseQuote != null && !responseQuote.isBlank(), "selected previous response source is empty");
            }
            object.put("responseQuote", responseQuote);
            return parse(JSON.writeValueAsString(object));
        } catch (java.io.IOException e) { throw new IllegalArgumentException("invalid guidance JSON", e); }
    }

    public static Match parse(String raw) {
        try {
            var node = JSON.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(raw);
            require(node != null && node.isObject(), "guidance match must be an object");
            var fields = new java.util.HashSet<String>();
            node.fieldNames().forEachRemaining(fields::add);
            require(fields.equals(java.util.Set.of("ruleId", "sameRequestRound", "requestQuote", "amountYuan", "purpose",
                    "recipient", "previousResponse", "responseQuote", "blocked", "blockingQuote")), "invalid guidance fields");
            require(node.path("sameRequestRound").isInt() && node.path("amountYuan").isInt()
                    && node.path("blocked").isBoolean(), "invalid guidance field types");
            require(node.path("ruleId").isNull() || node.path("ruleId").isTextual(), "ruleId must be text or null");
            for (String name : List.of("requestQuote", "recipient", "previousResponse", "responseQuote", "blockingQuote"))
                require(node.path(name).isTextual() && node.path(name).asText().length() <= 1000, "invalid " + name);
            require(node.path("purpose").isNull() || node.path("purpose").isTextual(), "invalid purpose");
            return JSON.treeToValue(node, Match.class);
        } catch (java.io.IOException e) { throw new IllegalArgumentException("invalid guidance JSON", e); }
    }

    public static List<SceneRuleSnapshot> rules(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var node = JSON.readTree(json);
            var result = new java.util.ArrayList<SceneRuleSnapshot>();
            for (var rule : node) if ("VICTIM".equals(rule.path("targetRole").asText()))
                result.add(JSON.treeToValue(rule, SceneRuleSnapshot.class));
            return List.copyOf(result);
        } catch (java.io.IOException e) { throw new IllegalStateException("Unreadable frozen victim rules", e); }
    }

    public record Match(String ruleId, int sameRequestRound, String requestQuote, int amountYuan,
            FundPurpose purpose, String recipient, String previousResponse, String responseQuote,
            boolean blocked, String blockingQuote) {}

    public static VictimGuidance decide(int roundNo, Match match, List<SceneRuleSnapshot> rules,
            List<VictimGuidance> saved, List<DialogueLine> history, DialogueLine current, boolean blocked) {
        if (match.ruleId() == null) return null;
        String proposedRecipient = match.purpose() == FundPurpose.INVESTMENT_PAYMENT && match.recipient() != null
                && match.recipient().isBlank() ? FundRules.INVESTMENT_RECIPIENT : match.recipient();
        // Dialogue alone cannot supply a saved request or a refusal count.
        int previousRound = saved.isEmpty() ? 0 : match.sameRequestRound();
        require(previousRound >= 0 && previousRound < roundNo, "invalid previous request round");
        var rule = rules.stream().filter(r -> "VICTIM".equals(r.targetRole()) && r.ruleId().equals(match.ruleId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown frozen victim rule"));
        require(current.roundNo() == roundNo && current.speaker() == SpeakerRole.SCAMMER
                && contains(current, match.requestQuote()), "request quote must come from current scammer message; "
                    + "requestQuote须逐字复制本轮联系人content或action中的一段连续原文（1至500字），不可概括、拼接或沿用历史引用");
        require(match.amountYuan() >= 0 && proposedRecipient != null && proposedRecipient.length() <= 160,
                "invalid request amount/recipient");
        require(match.amountYuan() == 0 || match.purpose() != null, "payment amount requires a purpose");
        require(match.purpose() == null || switch (match.purpose()) {
            case INITIAL_PAYMENT, ADDITIONAL_PAYMENT, INVESTMENT_PAYMENT, EXTRA_FEE_PAYMENT, LOAN_TRANSFER, OTHER_PAYMENT -> true;
            default -> false;
        }, "not a payment purpose");
        var referenced = saved.stream().filter(d -> d.roundNo() == previousRound).findFirst().orElse(null);
        require(previousRound == 0 || referenced != null, "unknown previous request; 可引用的已保存请求轮号="
                + saved.stream().map(VictimGuidance::roundNo).toList()
                + "；只能引用其中同请求的最近记录，不能使用仅出现在正式对话中的轮号。"
                + "没有同请求记录时sameRequestRound=0、previousResponse=NONE、responseQuote为空，不继承历史次数");
        // Payment completion is saved by the ledger; canonicalize stale links within that exact request chain.
        var canonical = referenced == null || match.purpose() == null ? referenced
                : saved.stream().filter(d -> d.requestId().equals(referenced.requestId()))
                    .max(java.util.Comparator.comparingInt(VictimGuidance::roundNo)).orElse(referenced);
        boolean stalePaymentLink = canonical != referenced;
        // An invented recipient is missing execution information, never permission to pay that recipient.
        String recipient = proposedRecipient.isBlank()
                || (canonical != null && canonical.recipient().equals(proposedRecipient))
                || (match.purpose() == FundPurpose.INVESTMENT_PAYMENT && FundRules.INVESTMENT_RECIPIENT.equals(proposedRecipient))
                || contains(current, proposedRecipient)
                || history.stream().filter(l -> l.speaker() == SpeakerRole.SCAMMER && l.roundNo() < roundNo)
                    .anyMatch(l -> contains(l, proposedRecipient)) ? proposedRecipient : "";
        // A changed request starts at one even when the model links it to an older request.
        var previous = canonical != null && canonical.ruleId().equals(match.ruleId())
                && canonical.amountYuan() == match.amountYuan() && canonical.purpose() == match.purpose()
                && (canonical.recipient().isBlank() || Objects.equals(canonical.recipient(), recipient)) ? canonical : null;
        String previousResponse = previous == null ? "NONE" : match.previousResponse();
        if (previous != null) {
            require(saved.stream().noneMatch(d -> d.requestId().equals(previous.requestId()) && d.roundNo() > previous.roundNo()),
                    "sameRequestRound must reference the most recent record of this request");
        }
        if (match.amountYuan() > 0 && (previous == null || previous.recipient().isBlank())) {
            String source = current.content() + "\n" + (current.action() == null ? "" : current.action());
            if (previous == null)
                require(amountGrounded(source, match.amountYuan()), "payment amount must be grounded in the current request");
        }
        boolean previousRefused = false, previousAccepted = false;
        if (previous != null && stalePaymentLink) {
            previousRefused = !previous.fulfilled();
        } else if (previous != null && !"NONE".equals(previousResponse)) {
            var response = history.stream().filter(l -> l.roundNo() == previous.roundNo() && l.speaker() == SpeakerRole.VICTIM)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("previous victim response missing"));
            require(contains(response, match.responseQuote()), "previous response requires verbatim evidence");
            previousRefused = "REFUSED".equals(previousResponse);
            previousAccepted = "ACCEPTED".equals(previousResponse);
            require(previousRefused || previousAccepted, "invalid previous response");
        } else require("NONE".equals(previousResponse), "invalid previous response");
        if (previous != null && match.purpose() != null && previousAccepted && !previous.fulfilled()) previousRefused = true;
        if (match.blocked()) require(contains(current, match.blockingQuote())
                || history.stream().anyMatch(l -> contains(l, match.blockingQuote())), "blocking fact requires evidence");
        int count = previous != null && previous.roundNo() == roundNo - 1 && previousRefused && !previous.fulfilled()
                ? Math.min(3, previous.consecutive() + 1) : 1;
        boolean fulfilled = previous != null && (previous.fulfilled() || (match.purpose() == null && previousAccepted));
        // Accepted explanations persist; an already executed payment can never be granted a second time.
        boolean required = !blocked && !match.blocked() && (count >= 3 || (fulfilled && match.purpose() == null));
        if (fulfilled && match.purpose() != null) required = false;
        return new VictimGuidance(roundNo, rule.ruleId(), previous == null ? rule.ruleId() + ":" + roundNo : previous.requestId(),
                match.requestQuote(), match.amountYuan(), match.purpose(), recipient, count, required,
                fulfilled, blocked || match.blocked(), rule.ruleText());
    }

    public VictimGuidance requireSmallPayment(FundsContext context) {
        if (required || blocked || fulfilled || purpose == null || amountYuan <= 0 || recipient.isBlank()
                || context == null || context.gate() == null) return this;
        var gate = context.gate();
        if (gate.score() < gate.small() || amountYuan > gate.smallCapYuan()
                || (long) context.totalPaidYuan() - context.totalRefundedYuan() + amountYuan > gate.smallCapYuan()
                || amountYuan > (long) context.availableBalanceYuan()
                    + context.generalReserveTotalYuan() - context.generalReserveReleasedYuan()) return this;
        return new VictimGuidance(roundNo, ruleId, requestId, requestQuote, amountYuan, purpose, recipient,
                consecutive, true, fulfilled, blocked, instruction);
    }

    public boolean paymentAllowed(FundsContext context) {
        return required && !blocked && !fulfilled && amountYuan > 0 && !recipient.isBlank()
                && (!context.v3() || context.totalPaidYuan() > 0 || amountYuan <= context.gate().smallCapYuan())
                && amountYuan <= (long) context.availableBalanceYuan()
                    + context.generalReserveTotalYuan() - context.generalReserveReleasedYuan();
    }

    public VictimGuidance complete() {
        return new VictimGuidance(roundNo, ruleId, requestId, requestQuote, amountYuan, purpose, recipient,
                consecutive, required, true, blocked, instruction);
    }

    public boolean awaitingRecipient(FundsContext context) {
        return required && !blocked && !fulfilled && purpose != null && amountYuan > 0 && recipient.isBlank()
                && (!context.v3() || context.totalPaidYuan() > 0 || amountYuan <= context.gate().smallCapYuan())
                && amountYuan <= (long) context.availableBalanceYuan()
                    + context.generalReserveTotalYuan() - context.generalReserveReleasedYuan();
    }

    public void validateBehavior(FundsContext context, String action, List<FundAction> actions) {
        if (required && !blocked && purpose == null) {
            require(action != null && action.matches("(?s).*(已接受|已同意|已配合|已放弃|已开始|已按|已继续|已实际|已完成).*")
                    && !action.matches("(?s).*(仍在等待|尚未|暂不|拒绝|准备|考虑|核实后|确认后).*"),
                    "本事项已决定配合，action须记录已执行的推进，不能只承诺或继续等待");
        }
        validateActions(context, roundNo, actions);
    }

    public void validateActions(FundsContext context, int currentRound, List<FundAction> actions) {
        if (blocked || (fulfilled && purpose != null)) require(actions.stream().noneMatch(a ->
                a.type() == FundFlowType.PAYMENT || a.type() == FundFlowType.RESERVE_RELEASE),
                "已停止、明确反证或同笔已付款，不得执行新的付款或预留释放");
        if (!required || fulfilled || blocked || purpose == null) return;
        if (!paymentAllowed(context)) {
            require(actions.stream().noneMatch(a -> a.type() == FundFlowType.PAYMENT || a.type() == FundFlowType.RESERVE_RELEASE),
                    "本事项超过余额及普通预留或缺少收款对象，不得付款、拆单或动用应急资金");
            return;
        }
        var payments = actions.stream().filter(a -> a.type() == FundFlowType.PAYMENT).toList();
        require(currentRound == roundNo && payments.size() == 1 && payments.getFirst().amountYuan() == amountYuan
                && payments.getFirst().purpose() == purpose, "本事项已决定付款，必须执行对应金额和用途，不能仅口头答应");
        require(actions.stream().allMatch(a -> a.type() == FundFlowType.PAYMENT
                || (a.type() == FundFlowType.RESERVE_RELEASE && a.reserveTier() == FundReserveTier.GENERAL)),
                "本事项许可只允许对应付款及普通预留，不允许应急预留或其他动作");
    }

    public String prompt(FundsContext context) {
        String decision = blocked ? "本事项已有明确反证或本人已停止往来，保持停止，不付款、不释放预留，不为延长演绎收回决定。"
                : context.v3() && !fulfilled && context.totalPaidYuan() == 0 && amountYuan > context.gate().smallCapYuan()
                    ? "本事项金额超过首次小额上限" + context.gate().smallCapYuan() + "元。本轮提出先小额试付，等待对方明确同意金额；fundActions=[]，不能直接付原金额或自行改金额入账。"
                : fulfilled ? "本事项此前已经完成，保持记忆，不重问旧疑点；已经付过的同一笔不得再付。回应当前新话题，不重复旧付款或再次询问已确认的收款。不得虚构独立核实完成。"
                : paymentAllowed(context)
                ? "本事项已经决定相信并付款：当轮实际支付" + amountYuan + "元，用途=" + purpose
                    + "，收款对象=" + recipient + "。只可为本笔缺口释放普通预留，禁止应急预留、拆单或改变金额用途。"
                : awaitingRecipient(context)
                    ? "本事项已决定接受本笔请求，金额及普通资金足够，只缺收款对象。本轮同意后直接询问应转给谁或充值到哪个模拟账户，"
                        + "不再以没摸熟、旧核实条件或一般谨慎拒绝；信息未齐全前不得付款或释放预留，不编造真实账号。"
                : required && purpose != null
                    ? "本事项尚不具备执行条件：金额不明、超出余额及普通预留或缺少收款对象。说明真实限度或只询问缺少的信息，禁止拆单、自改金额或动用应急资金。"
                : required && purpose == null
                    ? "本事项已经决定接受解释并配合：本轮明确同意并在action写出实际推进的行为，不再等待旧核实条件。不得虚构独立核实完成。"
                    : "尚未形成本事项强制配合决定；按真实资金、当前意愿和已知事实回应，不为凑次数付款。";
        return "\n【本轮受害者场景规则，控制指导不是人物所知事实】\n" + instruction + "\n" + decision
                + "本段只覆盖当前事项的一般谨慎和旧核实偏好，不覆盖资金事实、明确反证及已结束往来。"
                + "不在台词或心理中提到规则、次数、门槛或系统控制。";
    }

    private static boolean contains(DialogueLine line, String quote) {
        return quote != null && !quote.isBlank() && quote.length() <= 500
                && (line.content().contains(quote) || (line.action() != null && line.action().contains(quote)));
    }
    private static boolean amountGrounded(String source, int expected) {
        source = source.replaceAll("(?<=[0-9])[,，](?=[0-9]{3}(?:[^0-9]|$))", "");
        var tokens = java.util.regex.Pattern.compile("(?:转账?|支付|付|补交|缴纳|交|投入|入金|垫付|出资)\\s*([0-9零〇一二两三四五六七八九十百千万亿]+)(?![0-9.])|(?<![0-9.])([0-9零〇一二两三四五六七八九十百千万亿]+)\\s*(?:元|块)").matcher(source);
        while (tokens.find()) {
            String token = tokens.group(1) == null ? tokens.group(2) : tokens.group(1);
            // A final digit after a unit uses the next lower place: 三千五=3500; 三千零五 remains 3005.
            if (token.matches(".*[百千万][一二两三四五六七八九]$")) {
                char unit = token.charAt(token.length() - 2);
                token += switch (unit) { case '百' -> "十"; case '千' -> "百"; default -> "千"; };
            }
            long total = 0, section = 0, number = 0;
            boolean valid = true;
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                int digit = Character.isDigit(c) ? Character.digit(c, 10) : "零一二三四五六七八九".indexOf(c == '两' ? '二' : c == '〇' ? '零' : c);
                if (digit >= 0) number = number * 10 + digit;
                else {
                    long unit = switch (c) { case '十' -> 10; case '百' -> 100; case '千' -> 1000; case '万' -> 10000; case '亿' -> 100000000; default -> 0; };
                    if (unit == 0) { valid = false; break; }
                    if (unit < 10000) section += (number == 0 ? 1 : number) * unit;
                    else if (unit == 10000) { total += (section + number) * unit; section = 0; }
                    else { total = (total + section + number) * unit; section = 0; }
                    number = 0;
                }
                if (number > Integer.MAX_VALUE || total + section > Integer.MAX_VALUE) { valid = false; break; }
            }
            if (valid && total + section + number == expected) return true;
        }
        return false;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
