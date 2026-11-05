package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.simulation.domain.*;
import java.util.*;
import java.util.regex.Pattern;

/** Binds a completed outgoing event to its own amount, direction and tense, never every number in a sentence. */
final class PayoutClaims {
    private enum Kind { REFUND, REWARD, ANY }
    private record Claim(int amount, Kind kind, boolean newPayment, boolean historical, String evidence) {}
    private static final String DONE = "(?:已经?|实际|成功|完成(?=返|退|转|发放|支付|付款))";
    private static final String OP = "(?:返还|返款|退款|退还|退回|转账|转款|发放|支付|付款|到账|收到|到.{0,4}账户|返|退|转|付)";
    private static final String MONEY = "(?<amount>[0-9]+(?:\\.[0-9]+)?)(?<scale>万?)(?:元|块)";
    private static final String GAP = "(?:(?![0-9]+(?:\\.[0-9]+)?(?:万)?(?:元|块)|订单|任务|商品)[^，,。；;]){0,10}?";
    private static final List<Pattern> EVENTS = List.of(
            Pattern.compile(DONE + GAP + OP + GAP + MONEY),
            Pattern.compile(MONEY + GAP + DONE + GAP + OP),
            Pattern.compile(DONE + GAP + MONEY + GAP + OP),
            Pattern.compile(OP + "(?:还|回|给|款|账)*了" + GAP + MONEY),
            Pattern.compile(OP + GAP + MONEY + GAP + "(?:成功|完成|到账了?)"),
            Pattern.compile(MONEY + GAP + OP + "(?:了|成功|完成)"),
            Pattern.compile(MONEY + GAP + "到账了?"));
    private static final Pattern FUTURE = Pattern.compile("如果|只要|等.{0,20}(?:后|再)|(?:付款|支付|转账|垫付|下单|任务|操作)(?:完成|成功)?后");
    private static final Pattern NOT_DONE = Pattern.compile("未|没|不会|不要|准备|打算|承诺|答应|愿意|同意|稍后|随后|马上|(?<!已)(?<!已经)(?<!实际)(?<!成功)(?<!完成)将|会[给返转退付到]|要[给返转退付]");
    private static final Pattern HISTORY = Pattern.compile("之前|上轮|上次|昨天|先前|此前|刚才");
    private static final Pattern ADDITIONAL = Pattern.compile("(?:又|再次|另外|另行|额外)(?:已经?|实际|向你|给你|向您|给您){0,3}(?:发放|支付|转|返|退)");
    private static final Pattern INCOMING = Pattern.compile("^(?:你|您)(?:这边|这笔|刚刚|刚才|的|[0-9]+元|订单|款项|\\s)*(?:已经?|完成|实际|成功)?(?:付款|支付|转账|付了|转了)|(?:已收到|收到|看到).{0,6}(?:你|您).{0,10}(?:支付|付款|转账|转来|付)|^(?:我|我们|平台|商家)(?:这边)?(?:已经?|实际|刚刚)?收到");
    private static final Pattern MATERIAL = Pattern.compile("(?:截图|界面|屏幕|页面|图片).{0,4}(?:显示|写着|标注)");
    private static final Pattern EXECUTION = Pattern.compile("已经?|实际|成功|完成|(?:返|退|转)(?:还|回|给|款|账)*了|到账了");

    static String correctReceiptDenial(String text, String contactLine, String contactAction, FundsContext context) {
        if (text == null || context == null || !context.v3()) return text;
        for (String source : List.of(contactLine, contactAction == null ? "" : contactAction)) {
            for (var claim : claims(source)) {
                if (claim.newPayment() || context.existingFlows().stream()
                        .noneMatch(f -> matches(claim, f.getFlowType(), f.getAmountYuan()))) continue;
                var amount = Pattern.compile("(?<![0-9.])" + claim.amount() + "(?![0-9.])");
                var clauses = text.split("(?<=[，,。；;！!？?])", -1);
                boolean previousReceipt = false;
                for (int i = 0; i < clauses.length; i++) {
                    String clause = clauses[i];
                    // Only resolve a direct reference to the just-confirmed payout, never another pending payment.
                    if (previousReceipt && clause.matches("\\s*等(?:到)?(?:它|这笔(?:钱|款)?)(?:真|真的|真正|实际)?(?:落袋|到账|收到)(?:了|后|再)?[，,。；;]?")) {
                        clauses[i] = "";
                        previousReceipt = false;
                        continue;
                    }
                    previousReceipt = false;
                    if (clause.matches(".*(?:截图|凭证|图片|通知|证明).*")) continue;
                    Kind deniedKind = clause.matches(".*(?:本金|退款|返款).*") ? Kind.REFUND
                            : clause.matches(".*(?:报酬|佣金|返利|奖励).*") ? Kind.REWARD : Kind.ANY;
                    if (context.existingFlows().stream().noneMatch(f -> matches(
                            new Claim(claim.amount(), deniedKind, false, true, ""), f.getFlowType(), f.getAmountYuan()))) continue;
                    if (amount.matcher(clause).find()
                            && clause.matches(".*(?:没看到|没收到|未收到|没到账|未到账|没有到账|没有收到).*")
                            && !clause.matches(".*(?:不是|并非|如果|要是|假如|以后|下次|另一笔|又一笔).*"))
                        clauses[i] = "这笔" + claim.amount() + "元已收到。";
                    previousReceipt = amount.matcher(clauses[i]).find()
                            && clauses[i].matches(".*(?:已收到|收到了|已到账|到账了).*")
                            && !clauses[i].matches(".*(?:未|没|如果|要是|假如|另一笔|下次|以后|截图|凭证).*" );
                }
                text = String.join("", clauses);
            }
        }
        return text;
    }

    static String correctConfirmedPaymentQuestion(String text, String contactLine, FundsContext context) {
        if (text == null || context == null || !context.v3()) return text;
        for (var flow : context.existingFlows()) {
            if (flow.getFlowType() != FundFlowType.PAYMENT) continue;
            String amount = "(?<![0-9.])" + flow.getAmountYuan() + "(?![0-9.])";
            if (!contactLine.matches("(?s).*" + amount + "元[^，。；!?！？]{0,16}(?:已确认|确认收到|已收到|已到账).*")) continue;
            // Resolve only this paid amount and a request to the contact, never a question about incoming profits.
            text = text.replaceAll(amount + "元(?:解冻金|手续费|款项)?(?:刚才|刚刚)?(?:已经?|实际)?(?:转|付|支付)(?:了)?[，,]"
                    + "(?:您|你)(?:看下|看看|查下|确认下)?(?:是否|有没有|是不是)(?:已经?)?到账[？?]?",
                    "这笔款您已确认收到。");
        }
        return text;
    }

    static void validate(String line, String action, List<FundAction> actions, FundsContext context) {
        for (String text : List.of(line == null ? "" : line, action == null ? "" : action)) {
            for (var claim : claims(text)) {
                boolean current = actions.stream().anyMatch(a -> matches(claim, a.type(), a.amountYuan()));
                boolean saved = !claim.newPayment() && context.existingFlows().stream()
                        .anyMatch(f -> matches(claim, f.getFlowType(), f.getAmountYuan()));
                if (!current && !saved) throw AgentOutputParser.fail(AgentCallException.Category.DIALOGUE,
                        "completed payout claim requires a matching fund action; 出款金额=" + claim.amount()
                        + "元，类型=" + claim.kind() + "，" + (claim.newPayment() ? "本轮新出款" : "到账/历史确认")
                        + "；当前出款必须有对应动作，确认旧款只能引用同类已保存流水。任务金额、受害者付款及未来承诺不要写成已出款。若无对应可用流水且未实际出款，删除该到账/返款说法，改为尚未出款或明确的模拟收益展示；不要新增虚假fundActions凑校验。");
            }
        }
    }

    static void validateAction(FundAction action, String line, String behavior, FundsContext context,
            List<com.aiscam.antiscam.agent.AgentModels.DialogueLine> history, int roundNo) {
        if (!Pattern.compile("(?<![0-9.])" + action.amountYuan() + "(?![0-9.])").matcher(action.quote()).find())
            throw AgentOutputParser.fail(AgentCallException.Category.DIALOGUE,
                    "fund quote is not a new executed action; quote is missing the Arabic amount matching amountYuan"
                    + "; type=" + action.type() + ", amountYuan=" + action.amountYuan()
                    + "；本金与报酬分别记账，各自quote须逐字引用本轮含对应金额的实际执行句，不能引用本息合计金额支持拆分动作。"
                    + "未实际执行时fundActions=[]，不得为通过校验虚构出款。");
        boolean executed = false;
        for (String source : List.of(line == null ? "" : line, behavior == null ? "" : behavior)) {
            if (!source.contains(action.quote())) continue;
            for (var quoted : claims(action.quote())) {
                if (!matches(quoted, action.type(), action.amountYuan()) || quoted.historical()
                        || !EXECUTION.matcher(quoted.evidence()).find()) continue;
                if (claims(source).stream().anyMatch(full -> matches(full, action.type(), action.amountYuan())
                        && !full.historical() && (full.evidence().contains(quoted.evidence()) || quoted.evidence().contains(full.evidence()))))
                    executed = true;
            }
        }
        boolean repeated = history.stream().filter(l -> l.roundNo() < roundNo && l.speaker() == SpeakerRole.SCAMMER)
                .anyMatch(l -> l.content().contains(action.quote()) || (l.action() != null && l.action().contains(action.quote())));
        boolean paidBefore = context.existingFlows().stream().anyMatch(f -> f.getFlowType() == action.type()
                && f.getAmountYuan() == action.amountYuan() && (action.type() != FundFlowType.REFUND
                    || Objects.equals(f.getRelatedPaymentId(), action.relatedPaymentId())));
        if (!executed || repeated || (paidBefore && !ADDITIONAL.matcher(action.quote()).find())) {
            String reason = !executed
                    ? "当前quote未匹配该类型和金额的已完成出款，或包含条件、否定、截图展示、历史描述。"
                    : repeated ? "quote逐字重复此前骗子消息中的动作，不能作为本轮新出款。"
                    : "同类型同金额的出款已入账（本金退款还须关联同一付款），本轮未明确又/再次/另外执行。";
            String execution = action.type() == FundFlowType.REFUND
                    ? "已实际退还" + action.amountYuan() + "元本金"
                    : "已实际发放" + action.amountYuan() + "元报酬";
            throw AgentOutputParser.fail(AgentCallException.Category.DIALOGUE,
                    (claims(action.quote()).isEmpty() ? "fund quote is not a new executed action"
                            : action.type() == FundFlowType.REWARD ? "reward is not a new executed payout" : "fund quote is not a new executed refund")
                    + "; type=" + action.type() + ", amountYuan=" + action.amountYuan()
                    + ", relatedPaymentId=" + action.relatedPaymentId() + "；" + reason
                    + "本金与报酬分别执行、分别记账，各自quote须含对应金额，不能用本息合计的执行句支持拆分金额。"
                    + "仅在本轮确实执行且未重复入账时，在action独立句写明“" + execution
                    + "”，quote逐字引用该句，条件及其他金额另句表达。"
                    + "若只是未来承诺、旧款确认或尚未出款，fundActions=[]，同步删除本轮已出款说法；不得为通过校验虚构出款或重复退款。");
        }
    }

    private static boolean matches(Claim claim, FundFlowType type, int amount) {
        return amount == claim.amount() && switch (claim.kind()) {
            case REFUND -> type == FundFlowType.REFUND;
            case REWARD -> type == FundFlowType.REWARD;
            case ANY -> type == FundFlowType.REFUND || type == FundFlowType.REWARD;
        };
    }

    private static Set<Claim> claims(String text) {
        var result = new LinkedHashSet<Claim>();
        for (String sentence : text.split("[。；;！!？?\\n]")) {
            boolean conditional = false;
            for (String part : sentence.split("[，,]|但是|不过|然而|并且|同时|但|并")) {
                String clause = part.strip();
                if (clause.matches("^(现在|目前|实际上).*")) conditional = false;
                for (var pattern : EVENTS) {
                    var event = pattern.matcher(clause);
                    while (event.find()) {
                        String prefix = clause.substring(0, event.start());
                        String evidence = event.group();
                        if (clause.substring(event.end()).matches("^(?:了|给你|给您|\\s)*[0-9]+(?:元|块).*")) continue;
                        if (prefix.matches(".*(?:你|您)(?:投入|充值|入金|转入|支付|转账)的?$")) continue;
                        if (conditional || FUTURE.matcher(prefix + evidence).find()
                                || NOT_DONE.matcher(prefix + evidence).find()
                                || MATERIAL.matcher(prefix).find() || INCOMING.matcher(prefix + evidence).find()) continue;
                        int amount;
                        try {
                            var number = new java.math.BigDecimal(event.group("amount"));
                            amount = (event.group("scale").isEmpty() ? number : number.movePointRight(4)).intValueExact();
                        } catch (ArithmeticException e) {
                            throw AgentOutputParser.fail(AgentCallException.Category.DIALOGUE, "出款声明必须使用可入账的整数元金额");
                        }
                        String local = prefix.substring(Math.max(0, prefix.length() - 3)) + evidence;
                        // A completed verb may precede the noun: 已发放8元报酬.
                        int end = event.end();
                        local += clause.substring(end, Math.min(clause.length(), end + 3));
                        Kind kind = local.matches(".*(本金|退款|返款|退还|退回|返还).*") ? Kind.REFUND
                                : local.matches(".*(报酬|返利|佣金|奖励).*") ? Kind.REWARD : Kind.ANY;
                        boolean historical = HISTORY.matcher(prefix + evidence).find();
                        boolean additional = ADDITIONAL.matcher(prefix + evidence).find();
                        result.add(new Claim(amount, kind, additional && !historical, historical, evidence));
                    }
                }
                if (FUTURE.matcher(clause).find()) conditional = true;
            }
        }
        return result;
    }
}
