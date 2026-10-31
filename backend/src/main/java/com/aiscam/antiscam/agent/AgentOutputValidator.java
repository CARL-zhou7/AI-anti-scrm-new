package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentCallException.Category;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.FundRules;
import com.aiscam.antiscam.simulation.rules.FactCode;
import com.aiscam.antiscam.simulation.rules.OutcomeRules;
import com.aiscam.antiscam.simulation.rules.ScenarioGoalRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Business validation of parsed Agent output (plan Task 6 rules 1-6). Pure functions: nothing here touches the
 * database, and the program never re-judges on the model's behalf — a mismatch is a retryable failure.
 */
public final class AgentOutputValidator {
    private static final Pattern FUND_NOT_EXECUTED = Pattern.compile(
            "之前|上轮|上次|昨天|曾经|先前|未|没|准备|打算|(?<!已)(?<!已经)(?<!实际)(?<!成功)(?<!完成)将|稍后|以后|承诺|如果|等会|不会|不要|会[给返转退付]|要[给返转退付]");
    private static final Pattern FUND_EXECUTED = Pattern.compile(
            "(已经?|实际|完成|成功).{0,24}(转|付|支付|返|退|发放|到账|释放|动用)|(转账|转款|转|付款|支付|返款|返还|退回|退款|发放|到账|释放|动用)(了|成功|完成)");
    private static final Pattern FUND_INTENTION = Pattern.compile("愿意|答应|同意|告知先|表示先|声称|承诺|做完|完成任务后|截图后|等.{0,16}(后|再)");
    private static final Pattern REFUND_EXECUTED = Pattern.compile(
            "(已经?|实际|完成|成功).{0,12}(返|退|转|到账|到.{0,4}账户)|(返|退|转)(还|回|给|款|账)*了|(返款|退款|转账)(成功|完成)|到账了");
    private static final Pattern ADDITIONAL_REFUND = Pattern.compile("(又|再次|另外|另行|额外|追加)(已经?|实际)?(返|退|转)");
    /** Hard cap per text item (decision D-4); the ~50/~40 character targets live in the prompts. */
    public static final int MAX_CODE_POINTS = 120;
    private static final Set<Integer> RISK_LEVELS = Set.of(0, 25, 50, 75, 100);
    private static final Pattern ACTION_IN_LINE = Pattern.compile("[（(【\\[][^）)】\\]]*?(转账|付款|点击|输入|发送|打开)[^）)】\\]]*?[）)】\\]]");
    private AgentOutputValidator() {}

    public static void trustEvent(TrustEventCandidate event, boolean attitude) {
        trustEvent(event, attitude, false);
    }

    /**
     * V1.4 keeps the legacy shape exactly: a trust-v1/v2 session may not use the new level/speaker/eventOrder
     * fields or the resolution type. trust-v3 requires the whole-round evidence shape.
     */
    public static void trustEvent(TrustEventCandidate event, boolean attitude, boolean trustV3) {
        if (event == null || event.type() == null || event.source() == null || event.subjectKey() == null
                || event.subjectKey().isBlank() || event.subjectKey().length() > 128 || event.quote() == null
                || event.quote().isBlank() || event.quote().length() > 255) throw fail(Category.ANALYSIS, "invalid event evidence");
        if (event.progressKey() != null && (event.progressKey().isBlank() || event.progressKey().length() > 128))
            throw fail(Category.ANALYSIS, "invalid event progressKey");
        if (attitude && event.progressKey() != null) throw fail(Category.ANALYSIS, "attitude event cannot introduce progressKey");
        if (event.materials() == null || event.materials().size()>10 || (attitude && (!event.materials().isEmpty() || event.requirement()!=null)))
            throw fail(Category.ANALYSIS,"invalid event details");
        for (var material : event.materials()) {
            if (!materialDetailsExplicit(material)) throw fail(Category.ANALYSIS,"material details are not explicit");
            if (material.amountYuan()<=0 || !event.quote().contains(Integer.toString(material.amountYuan()))
                    || !event.quote().contains(material.date()) || !event.quote().contains(material.accountName())
                    || !event.quote().contains(material.purpose())) throw fail(Category.ANALYSIS,"material details require verbatim evidence");
        }
        if (event.requirement()!=null) {
            var req=event.requirement(); waitingText(req.subjectKey(),128); waitingText(req.text(),255); waitingText(req.basis(),255);
            if (req.type()!=WaitingItemType.MATERIAL && req.type()!=WaitingItemType.INFORMATION) throw fail(Category.ANALYSIS,"invalid requirement type");
        }
        boolean isAttitude = event.type() == com.aiscam.antiscam.simulation.rules.TrustEventType.INDEPENDENT_SUPPORT
                || event.type() == com.aiscam.antiscam.simulation.rules.TrustEventType.INDEPENDENT_DOUBT;
        if (!trustV3) {
            if (attitude != isAttitude) throw fail(Category.ANALYSIS, "event type does not belong to this task");
            if (event.counterevidenceLevel() != null || event.speaker() != null || event.eventOrder() != null
                    || event.type().v3Only())
                throw fail(Category.ANALYSIS, "legacy session cannot use v3 trust event fields");
            return;
        }
        if (attitude) throw fail(Category.ANALYSIS, "v3 events are collected as whole-round evidence, not attitude events");
        if (event.progressKey() == null || event.progressKey().isBlank())
            throw fail(Category.ANALYSIS, "v3 event requires progressKey");
        if (event.speaker() == null || (event.speaker() != SpeakerRole.SCAMMER && event.speaker() != SpeakerRole.VICTIM))
            throw fail(Category.ANALYSIS, "v3 event requires speaker SCAMMER or VICTIM");
        if (event.eventOrder() == null || event.eventOrder() < 1)
            throw fail(Category.ANALYSIS, "v3 event requires a positive eventOrder");
        if (event.type() == com.aiscam.antiscam.simulation.rules.TrustEventType.DECISIVE_COUNTEREVIDENCE) {
            if (event.counterevidenceLevel() == null)
                throw fail(Category.ANALYSIS, "decisive counterevidence requires PARTIAL or CONFIRMED");
        } else if (event.counterevidenceLevel() != null) {
            throw fail(Category.ANALYSIS, "only decisive counterevidence may carry a level");
        }
        if (event.type() == com.aiscam.antiscam.simulation.rules.TrustEventType.INDEPENDENT_SUPPORT
                || event.type() == com.aiscam.antiscam.simulation.rules.TrustEventType.INDEPENDENT_DOUBT) {
            if (event.speaker() != SpeakerRole.VICTIM)
                throw fail(Category.ANALYSIS, "independent attitude events must cite the victim");
        }
    }

    public static boolean eventQuoteMatches(TrustEventCandidate event, String content, String action) {
        String original = event.source() == EvidenceSource.CONTENT ? content : action;
        return original != null && original.contains(event.quote());
    }

    static boolean materialDetailsExplicit(Material material) {
        waitingText(material.subjectKey(),128); waitingText(material.date(),64);
        waitingText(material.accountName(),64); waitingText(material.purpose(),64);
        return material.date().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}|(?:[0-9]{4}年)?[0-9]{1,2}月[0-9]{1,2}日")
                && !material.accountName().matches(".*(某|不详|未知|未提供|不明确|未注明|待补充).*" )
                && !material.purpose().matches(".*(某|不详|未知|未提供|不明确|未注明|待补充).*" );
    }

    public static void waitingUpdate(WaitingUpdate update) {
        if (update == null || update.action()==null || (update.type()!=WaitingItemType.CONSULTATION && update.type()!=WaitingItemType.VERIFICATION))
            throw fail(Category.ANALYSIS,"invalid waiting update type");
        waitingText(update.subjectKey(),128); waitingText(update.text(),255); waitingText(update.basis(),255);
        if (update.type()==WaitingItemType.CONSULTATION && !Set.of("consult:claimed-person","consult:third-party").contains(update.subjectKey()))
            throw fail(Category.ANALYSIS,"invalid consultation target");
        if (update.type()==WaitingItemType.CONSULTATION && update.action()==WaitingAction.OPEN
                && update.subjectKey().equals("consult:claimed-person") && !update.basis().matches(".*(原有|原来|通讯录|旧号码|原号码).*"))
            throw fail(Category.ANALYSIS,"claimed person requires the original contact channel");
        if (update.action()==WaitingAction.RESPOND && !update.basis().matches(".*(独立|官方|银行|原有|原来|通讯录|原号码).*"))
            throw fail(Category.ANALYSIS,"verification requires an independent channel");
        if (update.action()==WaitingAction.OPEN && !com.aiscam.antiscam.simulation.rules.WaitingItemRules.executed(update.basis(),false))
            throw fail(Category.ANALYSIS,"waiting action has not been sent; waitingUpdates.OPEN 的 basis 必须逐字引用本轮受害者已实际发起咨询或核实的台词/action。"
                    + "仅索要材料、表示我查一下或准备核实不算执行；删除没有执行证据的事项，没有其他有效事项时输出 waitingUpdates=[]，不得编造动作或改写原文。");
        if (update.action()==WaitingAction.RESPOND && !com.aiscam.antiscam.simulation.rules.WaitingItemRules.executed(update.basis(),true))
            throw fail(Category.ANALYSIS,"verification has not completed");
        if (update.action()==WaitingAction.ABANDON && !update.basis().matches(".*(不再等|不等|放弃|停止|取消|不再核实).*"))
            throw fail(Category.ANALYSIS,"waiting abandonment is not explicit");
        if (update.type()==WaitingItemType.CONSULTATION && update.action()==WaitingAction.RESPOND)
            throw fail(Category.ANALYSIS,"consultation replies come from fixed rules");
    }
    private static void waitingText(String text, int max) {
        if (text==null || text.isBlank() || text.length()>max) throw fail(Category.ANALYSIS,"invalid waiting text");
    }

    public static ReportContent report(ReportContent content, int expectedRounds, String outcomeDisplayText) {
        if (content == null || expectedRounds < 1 || content.roundAnalyses() == null
                || content.roundAnalyses().size() != expectedRounds) {
            throw new ReportValidationException("round count mismatch");
        }
        reportText(content.summary(), 600);
        reportText(content.speechAnalysis(), 600);
        reportText(content.victimBehaviorAnalysis(), 600);
        reportText(content.outcomeAnalysis(), 600);
        for (int i = 0; i < expectedRounds; i++) {
            var round = content.roundAnalyses().get(i);
            if (round == null || round.roundNo() != i + 1) throw new ReportValidationException("round order mismatch");
            reportText(round.analysis(), 300);
        }
        if (outcomeDisplayText == null || outcomeDisplayText.isBlank() || !content.outcomeAnalysis().contains(outcomeDisplayText)) {
            throw new ReportValidationException("authoritative outcome missing");
        }
        return content;
    }

    public static ReportContentV2 reportV2(ReportContentV2 content, String outcome, ReportFundsSummary funds,
            java.util.Map<Integer, List<String>> quotes) {
        if (content == null || content.contentSchemaVersion() != 2 || content.result() == null
                || content.keyNodes() == null || content.keyNodes().size() > 3
                || content.preventionAdvice() == null || content.preventionAdvice().size() < 2
                || content.preventionAdvice().size() > 3 || !java.util.Objects.equals(content.fundsSummary(),funds)) {
            throw new ReportValidationException("invalid report structure or funds");
        }
        reportText(content.summary(),800);
        reportText(content.result().text(),300); reportText(content.result().reason(),300);
        if (outcome == null || !content.result().text().contains(outcome)) throw new ReportValidationException("outcome mismatch");
        for (var node : content.keyNodes()) {
            if (node == null || !quotes.containsKey(node.roundNo())) throw new ReportValidationException("missing key round");
            reportText(node.analysis(), node.title() == null ? 300 : 140);
            if (node.title() != null) {
                reportText(node.title(), 32);
                if (node.title().matches("(?:关键节点|第[0-9一二三四五六七八九十]+轮)"))
                    throw new ReportValidationException("event title required");
            }
            if (node.quote() != null) {
                reportText(node.quote(),120);
                if (quotes.get(node.roundNo()).stream().noneMatch(line -> line.contains(node.quote()))) throw new ReportValidationException("quote not found");
            }
        }
        for (var advice : content.preventionAdvice()) reportText(advice,300);
        return content;
    }

    public static ReportContentV3 reportV3(ReportContentV3 content, String outcome, ReportFundsSummaryV3 funds,
            Map<Integer,List<String>> quotes) {
        reportStructureV3(content, funds, quotes);
        String expected = outcome == null ? "未形成受骗或识破结论" : outcome;
        if (outcome != null && !content.result().text().contains(outcome)) throw new ReportValidationException("outcome mismatch");
        if (outcome == null) {
            String narrative = content.result().text()+content.result().reason()+content.summary()
                    +content.keyNodes().stream().map(ReportKeyNode::analysis).collect(java.util.stream.Collectors.joining())
                    +String.join("",content.preventionAdvice());
            if (narrative.contains("已受骗") || narrative.contains("已识破")) throw new ReportValidationException("outcome invented");
        }
        return content;
    }

    /**
     * V1.4 keeps the v3 structure, funds and quote rules but has its own result wording: a finished run must quote the
     * program verdict, and a user exit must say that no verdict was formed. Describing earlier payments is fine.
     */
    public static ReportContentV3 reportV4(ReportContentV3 content, String outcome, ReportFundsSummaryV3 funds,
            Map<Integer,List<String>> quotes) {
        reportStructureV3(content, funds, quotes);
        String narrative = content.result().text()+content.result().reason()+content.summary()
                +content.keyNodes().stream().map(ReportKeyNode::analysis).collect(java.util.stream.Collectors.joining())
                +String.join("",content.preventionAdvice());
        if (outcome == null) {
            // Pinned v3 templates use the equivalent older wording for an unfinished run.
            if (!narrative.contains("未形成胜负结论") && !narrative.contains("未形成受骗或识破结论"))
                throw new ReportValidationException("a user exit report must state that no verdict was formed");
            if (narrative.contains("骗子胜") || narrative.contains("受害者胜"))
                throw new ReportValidationException("a user exit report cannot invent a verdict");
        } else {
            if (!content.result().text().contains(outcome)) throw new ReportValidationException("outcome mismatch");
            String other = outcome.equals("骗子胜") ? "受害者胜" : "骗子胜";
            if (narrative.contains(other)) throw new ReportValidationException("conflicting verdict");
        }
        return content;
    }

    /** Structure, funds ledger and quote rules shared by the v3 and v4 wording checks. */
    private static void reportStructureV3(ReportContentV3 content, ReportFundsSummaryV3 funds,
            Map<Integer,List<String>> quotes) {
        if (content == null || content.contentSchemaVersion() != 3 || funds == null || !funds.equals(content.fundsSummary())
                || funds.totalPaidYuan()<0 || funds.totalRefundedYuan()<0 || funds.totalRewardedYuan()<0
                || funds.availableBalanceYuan()<0 || funds.generalReserveRemainingYuan()<0 || funds.emergencyReserveRemainingYuan()<0
                || funds.actualLossYuan()!=FundRules.actualLoss(funds.totalPaidYuan(),funds.totalRefundedYuan(),funds.totalRewardedYuan()))
            throw new ReportValidationException("invalid report funds");
        if (content.result() == null || content.keyNodes() == null || content.keyNodes().size() > 3
                || content.preventionAdvice() == null || content.preventionAdvice().size() < 2
                || content.preventionAdvice().size() > 3) throw new ReportValidationException("invalid report structure");
        reportText(content.summary(),800);
        reportText(content.result().text(),300); reportText(content.result().reason(),300);
        for (var node : content.keyNodes()) {
            if (node == null || !quotes.containsKey(node.roundNo())) throw new ReportValidationException("missing key round");
            reportText(node.analysis(), node.title() == null ? 300 : 140);
            if (node.title() != null) {
                reportText(node.title(), 32);
                if (node.title().matches("(?:关键节点|第[0-9一二三四五六七八九十]+轮)"))
                    throw new ReportValidationException("event title required");
            }
            if (node.quote() != null) {
                reportText(node.quote(),120);
                if (quotes.get(node.roundNo()).stream().noneMatch(line -> line.contains(node.quote()))) throw new ReportValidationException("quote not found");
            }
        }
        for (var advice : content.preventionAdvice()) reportText(advice,300);
    }

    private static void reportText(String text, int max) {
        // Defensive output limits, not product copy limits; count Unicode code points.
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > max
                || text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
            throw new ReportValidationException("invalid report text");
        }
    }

    public static ScammerTurn validate(ScammerTurn turn) {
        return new ScammerTurn(line(turn.line()), action(turn.action()), required(turn.intent(), Category.DIALOGUE), turn.tone(), turn.claimedRefundYuan(), turn.fundActions());
    }

    public static VictimTurn validate(VictimTurn turn) {
        return new VictimTurn(line(turn.line()), action(turn.action()), required(turn.psychology(), Category.DIALOGUE), turn.tone(), turn.fundActions());
    }

    public static void funds(SpeakerRole role, String line, String action, Integer claimed, List<FundAction> actions,
            int roundNo, FundsContext context, List<DialogueLine> history) {
        funds(role, line, action, claimed, actions, roundNo, context, history, false);
    }

    public static void funds(SpeakerRole role, String line, String action, Integer claimed, List<FundAction> actions,
            int roundNo, FundsContext context, List<DialogueLine> history, boolean v12) {
        if (context == null) {
            if (claimed != null || !actions.isEmpty()) throw fail(Category.DIALOGUE, "legacy session cannot post funds");
            return;
        }
        if (role == SpeakerRole.VICTIM && claimed != null) throw fail(Category.DIALOGUE, "victim cannot claim refunds");
        if (claimed != null && claimed < 0) throw fail(Category.DIALOGUE, "negative refund claim");
        if (role == SpeakerRole.SCAMMER && context.scenarioCode() == ScenarioCode.IMPERSONATION_LOAN
                && (claimed != null || !actions.isEmpty())) throw fail(Category.DIALOGUE, "loan cannot refund or claim structured refund");
        long refund = 0;
        for (var a : actions) {
            if (a.speaker() != role || a.roundNo() != roundNo || a.quote() == null || a.quote().isBlank()
                    || !(line.contains(a.quote()) || (action != null && action.contains(a.quote())))) {
                throw fail(Category.DIALOGUE, "fund quote must belong to current speaker message");
            }
            if ((role == SpeakerRole.SCAMMER) != (a.type() == FundFlowType.REFUND || a.type() == FundFlowType.REWARD)) throw fail(Category.DIALOGUE, "wrong fund action role");
            // shortcut: lexical execution checks cannot resolve every paraphrase; extend with real-model regression cases when observed.
            var amount = Pattern.compile("(?<![0-9.])" + a.amountYuan() + "(?![0-9.])");
            boolean executed = java.util.Arrays.stream(a.quote().split("[，。；,;！!？?]"))
                    .anyMatch(clause -> amount.matcher(clause).find()
                            && clause.matches(".*(转|付|返|到账|释放|动用|发放).*" )
                            && !FUND_NOT_EXECUTED.matcher(clause).find());
            if (a.type() == FundFlowType.REFUND) {
                String source = line.contains(a.quote()) ? line : action;
                var quotedClauses = java.util.Arrays.stream(a.quote().split("[，。；,;！!？?]"))
                        .filter(clause -> amount.matcher(clause).find()).toList();
                boolean alreadyPosted = context.existingFlows().stream().anyMatch(f -> f.getFlowType() == FundFlowType.REFUND
                        && java.util.Objects.equals(f.getRelatedPaymentId(), a.relatedPaymentId()) && f.getAmountYuan() == a.amountYuan());
                executed = executed && java.util.Arrays.stream(source.split("[，。；,;！!？?]"))
                        .anyMatch(clause -> quotedClauses.stream().anyMatch(clause::contains)
                        && REFUND_EXECUTED.matcher(clause).find() && !FUND_NOT_EXECUTED.matcher(clause).find()
                        && !clause.matches(".*(愿意|答应|同意|告知先|表示先).*" )
                        && (!alreadyPosted || ADDITIONAL_REFUND.matcher(clause).find()));
            }
            boolean historical = a.quote().matches(".*(之前|上轮|上次|昨天|曾经|先前).*")
                    || history.stream().anyMatch(l -> l.roundNo() < roundNo && l.speaker() == role
                    && (l.content().contains(a.quote()) || (l.action() != null && l.action().contains(a.quote()))));
            if (!executed || historical) {
                String reason = !amount.matcher(a.quote()).find() ? "quote is missing the Arabic amount matching amountYuan"
                        : historical ? "quote repeats a historical action; do not post it again"
                        : "quote has no current non-negated execution clause; a promise or preparation must use fundActions=[]";
                throw fail(Category.DIALOGUE, "fund quote is not a new executed action; " + reason);
            }
            if (a.type() != FundFlowType.REFUND) {
                // Inspect the containing clause too: a quote may omit a preceding negation or condition.
                String source = line.contains(a.quote()) ? line : action;
                boolean completed = java.util.Arrays.stream(a.quote().split("[，。；,;！!？?]"))
                        .filter(clause -> amount.matcher(clause).find() && FUND_EXECUTED.matcher(clause).find())
                        .anyMatch(quoted -> java.util.Arrays.stream(source.split("[，。；,;！!？?]"))
                                .anyMatch(clause -> clause.contains(quoted) && !FUND_NOT_EXECUTED.matcher(clause).find()
                                        && !FUND_INTENTION.matcher(clause).find()));
                boolean paidBefore = a.type() == FundFlowType.REWARD && context.existingFlows().stream()
                        .anyMatch(f -> f.getFlowType() == FundFlowType.REWARD && f.getAmountYuan() == a.amountYuan());
                if (!completed || (paidBefore && !a.quote().matches(".*(又|再次|另外|另行|额外).*(支付|转|发放).*"))) {
                    throw fail(Category.DIALOGUE, a.type() == FundFlowType.REWARD
                            ? "reward is not a new executed payout" : "fund quote requires a completed payment or reserve release");
                }
            }
            if (a.type() == FundFlowType.REFUND || a.type() == FundFlowType.REWARD) refund += a.amountYuan();
        }
        if (role == SpeakerRole.SCAMMER && context.v3()) {
            for (String clause : line.split("[，。；,;！!？?]")) {
                if (!FUND_EXECUTED.matcher(clause).find() || FUND_NOT_EXECUTED.matcher(clause).find()
                        || FUND_INTENTION.matcher(clause).find() || clause.matches(".*(截图|界面|显示|承诺).*") ) continue;
                var amounts = Pattern.compile("(?<![0-9.])([0-9]+)元").matcher(clause);
                while (amounts.find()) {
                    String amount = amounts.group(1);
                    boolean posted = actions.stream().anyMatch(a -> Integer.toString(a.amountYuan()).equals(amount))
                            || context.existingFlows().stream().anyMatch(f ->
                                    (f.getFlowType() == FundFlowType.REFUND || f.getFlowType() == FundFlowType.REWARD)
                                    && Integer.toString(f.getAmountYuan()).equals(amount));
                    if (!posted) throw fail(Category.DIALOGUE,
                            "completed payout claim requires a matching fund action; 已出款台词必须对应资金动作，未执行时改为尚未到账，不得编造出款");
                }
            }
        }
        if (refund > 0 && (claimed == null || refund > claimed)) throw fail(Category.DIALOGUE, "actual refund exceeds claim");
        try {
            FundRules.apply(context.summary(), context, roundNo, context.existingFlows().size() + 1, actions);
        } catch (IllegalArgumentException e) {
            long payments = actions.stream().filter(a -> a.type() == FundFlowType.PAYMENT).mapToLong(FundAction::amountYuan).sum();
            if (v12 && "payment exceeds balance".equals(e.getMessage())
                    && payments > (long) context.availableBalanceYuan() + context.releasableReserveYuan()) {
                throw new FundsUnavailableException();
            }
            throw fail(Category.DIALOGUE, e.getMessage());
        }
    }

    /**
     * @param lines every line up to and including the current round (the victim line may still be a candidate)
     * @param prior facts saved with the previous analysed round
     */
    public static ValidatedJudgement validate(NarratorJudgement j, ScenarioCode scenario, int roundNo,
            List<DialogueLine> lines, PriorFacts prior) {
        return validate(j, scenario, roundNo, lines, prior, null);
    }

    public static ValidatedJudgement validate(NarratorJudgement j, ScenarioCode scenario, int roundNo,
            List<DialogueLine> lines, PriorFacts prior, FundsSummary funds) {
        return validate(j, scenario, roundNo, lines, prior, funds, null);
    }
    public static ValidatedJudgement validate(NarratorJudgement j, ScenarioCode scenario, int roundNo,
            List<DialogueLine> lines, PriorFacts prior, FundsSummary funds, Boolean v2Goal) {
        return validate(j, scenario, roundNo, lines, prior, funds, v2Goal, false);
    }

    /**
     * @param trustV3 V1.4: the program decides the outcome, so the candidate fields must stay empty and the two
     *                short narration fields fall back to complete analyses when unusable. Every other rule (quotes, funds, cumulative facts, risk,
     *                goal reference) keeps applying.
     */
    public static ValidatedJudgement validate(NarratorJudgement j, ScenarioCode scenario, int roundNo,
            List<DialogueLine> lines, PriorFacts prior, FundsSummary funds, Boolean v2Goal, boolean trustV3) {
        boolean v12 = funds != null;
        var c = Category.ANALYSIS;
        for (var event : j.attitudeEvents()) {
            trustEvent(event, true);
            var line = lines.stream().filter(l -> l.roundNo() == roundNo && l.speaker() == SpeakerRole.VICTIM)
                    .findFirst().orElseThrow(() -> fail(c, "attitude needs victim message"));
            String original = event.source() == EvidenceSource.CONTENT ? line.content() : line.action();
            if (original == null || !original.contains(event.quote())) throw fail(c, "attitude quote not in current message");
        }
        for (var update : j.waitingUpdates()) {
            waitingUpdate(update);
            boolean quoted = lines.stream().filter(l -> l.roundNo() == roundNo && l.speaker() == SpeakerRole.VICTIM)
                    .anyMatch(l -> l.content().contains(update.basis()) || l.action() != null && l.action().contains(update.basis()));
            if (!quoted) throw fail(c, "waiting basis must quote the current victim message verbatim");
        }
        if (j.characterExit() && (!v12 || j.candidateOutcome() != null || j.cooperationStopped()
                || j.characterExitReason() == null || j.characterExitReason().isBlank())) throw fail(c, "invalid character exit");
        if (!j.characterExit() && j.characterExitReason() != null) throw fail(c, "unexpected exit reason");
        for (String text : List.of(j.fraudStage(), j.speechAnalysis(), j.methodAnalysis(), j.victimPsychology(), j.riskReason())) {
            if (text.isBlank() || text.codePointCount(0, text.length()) > MAX_CODE_POINTS) throw fail(c, "analysis text length");
        }
        if (trustV3) {
            if (!j.attitudeEvents().isEmpty())
                throw fail(c, "v3 narration collects whole-round evidence instead of attitude events");
        }
        if (!RISK_LEVELS.contains(j.riskScore())) throw fail(c, "risk score outside the five levels");
        if (j.goalAchieved() && !j.actualFraudOccurred()) throw fail(c, "goal requires actual fraud");
        if ((prior.actualFraud() && !j.actualFraudOccurred()) || (prior.goalAchieved() && !j.goalAchieved())) {
            throw fail(c, "cumulative facts cannot revert");
        }
        for (var update : j.waitingUpdates()) {
            waitingUpdate(update);
            if (lines.stream().noneMatch(l -> l.roundNo()==roundNo && l.speaker()==SpeakerRole.VICTIM
                    && (l.content().contains(update.basis()) || l.action()!=null && l.action().contains(update.basis()))))
                throw fail(c,"waiting update requires current victim evidence");
        }
        List<EvidenceItem> merged = EvidenceValidator.validateAndMerge(j.evidence(), prior.evidence(), roundNo, lines);
        boolean hasFraudFact = merged.stream().anyMatch(e -> e.factCode().isActualFraud());
        if (v12 && funds.totalPaidYuan() > 0 && !j.actualFraudOccurred()) throw fail(c, "saved payments are actual fraud");
        boolean monetaryFact = merged.stream().anyMatch(e -> switch (e.factCode()) {
            case INITIAL_PAYMENT_COMPLETED, ADDITIONAL_PAYMENT_COMPLETED, INVESTMENT_COMPLETED, EXTRA_FEE_PAID, LOAN_TRANSFER_COMPLETED -> true;
            default -> false;
        });
        if (v12 && monetaryFact && funds.totalPaidYuan() == 0) throw fail(c, "payment evidence requires posted funds; 当前累计实际付款为0。"
                + "删除付款类evidence：INITIAL_PAYMENT_COMPLETED、ADDITIONAL_PAYMENT_COMPLETED、INVESTMENT_COMPLETED、EXTRA_FEE_PAID、LOAN_TRANSFER_COMPLETED。"
                + "点击链接、同意体验、准备充值不是实际付款；不得改成OTHER_DANGEROUS_ACTION_EXECUTED来绕过资金校验。"
                + "没有其他实际受骗事实时actualFraudOccurred=false、goalAchieved=false，没有其他有效证据时evidence=[]；"
                + "按实际事实重新判断candidateOutcome与candidateReason，不得编造付款或修改已保存台词。");
        if (j.actualFraudOccurred() != hasFraudFact) throw fail(c, "actual fraud flag disagrees with evidence");
        var facts = merged.stream().map(e -> new ScenarioGoalRules.Fact(e.factCode(), e.roundNo(), e.eventOrder())).toList();
        boolean expectedGoal = v2Goal != null ? prior.goalAchieved() || v2Goal
                : v12 ? prior.goalAchieved() || ScenarioGoalRules.goalAchieved(scenario, facts, funds)
                : ScenarioGoalRules.goalAchieved(scenario, facts);
        if (j.goalAchieved() != expectedGoal) throw fail(c, "goal flag disagrees with scenario rule: goalAchieved must be "
                + expectedGoal + " for " + scenario + "; use validated evidence and funds, not the round count");
        boolean stopEvidence = j.evidence().stream().anyMatch(e -> e.factCode() == FactCode.COOPERATION_STOPPED && e.roundNo() == roundNo);
        if (j.cooperationStopped() && !stopEvidence) throw fail(c, "stop needs victim evidence in this round");
        if (j.candidateOutcome() == OutcomeCode.UNDETERMINED || j.candidateOutcome() == OutcomeCode.LIMIT_REACHED) throw fail(c, "UNDETERMINED is never a candidate");
        boolean decisive = merged.stream().anyMatch(e -> e.factCode() == FactCode.DECISIVE_COUNTEREVIDENCE_FOUND);
        if (trustV3) {
            if (j.candidateOutcome() != null || (j.candidateReason() != null && !j.candidateReason().isBlank()))
                throw fail(c, "v3 narration must leave the outcome to the program: candidateOutcome and candidateReason must be null");
        } else {
            if (j.candidateOutcome() != (v2Goal != null
                    ? OutcomeRules.expectedV2(j.goalAchieved(), j.actualFraudOccurred(), j.cooperationStopped(), decisive)
                    : v12 ? OutcomeRules.expected(j.goalAchieved(), j.actualFraudOccurred(), j.cooperationStopped()) : OutcomeRules.expectedLegacy(j.goalAchieved(), j.actualFraudOccurred(), j.cooperationStopped()))) {
                throw fail(c, "candidate outcome disagrees with outcome rules");
            }
            if ((j.candidateOutcome() == null) != (j.candidateReason() == null || j.candidateReason().isBlank())) {
                throw fail(c, "candidate reason must accompany candidate outcome");
            }
        }
        // Display copy must not discard an otherwise valid round; null uses the existing full-analysis fallback.
        if (trustV3) j = new NarratorJudgement(j.fraudStage(),j.speechAnalysis(),j.methodAnalysis(),
                j.victimPsychology(),j.riskScore(),j.riskReason(),j.actualFraudOccurred(),j.goalAchieved(),
                j.cooperationStopped(),j.evidence(),j.candidateOutcome(),j.candidateReason(),j.characterExit(),
                j.characterExitReason(),j.attitudeEvents(),j.waitingUpdates(),
                narrationShort(j.methodShort(),2,12),narrationShort(j.intentShort(),12,40));
        return new ValidatedJudgement(j, merged);
    }

    private static String narrationShort(String text, int min, int max) {
        if (text == null) return null;
        String trimmed = text.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        return length >= min && length <= max ? trimmed : null;
    }

    public static boolean earlyException(NarratorJudgement j, int roundNo) {
        if (j.actualFraudOccurred() && (j.goalAchieved() || j.cooperationStopped())) return true;
        return j.cooperationStopped() && !j.goalAchieved() && j.evidence().stream().anyMatch(e ->
                e.roundNo() == roundNo && e.factCode() == FactCode.DECISIVE_COUNTEREVIDENCE_FOUND)
                && j.evidence().stream().anyMatch(e -> e.roundNo() == roundNo && e.factCode() == FactCode.COOPERATION_STOPPED);
    }

    private static String line(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > MAX_CODE_POINTS) throw fail(Category.DIALOGUE, "line length must be 1 to 120 Unicode characters");
        if (ACTION_IN_LINE.matcher(trimmed).find()) throw fail(Category.DIALOGUE, "action narration inside line");
        return trimmed;
    }

    private static String action(String action) {
        if (action == null) return null;
        String trimmed = action.strip();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > MAX_CODE_POINTS) throw fail(Category.DIALOGUE, "action length must be 1 to 120 Unicode characters, or null");
        return trimmed;
    }

    private static String required(String text, Category c) {
        if (text == null || text.isBlank()) throw fail(c, "internal note required");
        return text.strip();
    }

    static AgentCallException fail(Category c, String message) { return new AgentCallException(c, true, message); }

    /** Evidence rules (DB design §7.4): quotes are verbatim, only the victim's own behaviour counts. */
    public static final class EvidenceValidator {
        private EvidenceValidator() {}

        public static List<EvidenceItem> validateAndMerge(List<EvidenceItem> current, List<EvidenceItem> prior, int roundNo,
                List<DialogueLine> lines) {
            var c = Category.ANALYSIS;
            for (EvidenceItem e : current) {
                if (e.speaker() != SpeakerRole.VICTIM) throw fail(c, "facts may only cite the victim");
                if (e.roundNo() < 1 || e.roundNo() > roundNo || e.eventOrder() < 1) throw fail(c, "evidence position");
                DialogueLine line = lines.stream().filter(l -> l.roundNo() == e.roundNo() && l.speaker() == e.speaker())
                        .findFirst().orElseThrow(() -> fail(c, "evidence cites a missing line"));
                String source = e.source() == EvidenceSource.CONTENT ? line.content() : line.action();
                if (e.quote().isBlank() || source == null || !source.contains(e.quote())) throw fail(c, "quote not found verbatim");
            }
            for (EvidenceItem a : current) {
                for (EvidenceItem b : current) {
                    boolean samePlace = a.roundNo() == b.roundNo() && a.source() == b.source() && a.quote().equals(b.quote());
                    if (samePlace && pair(a.factCode(), b.factCode())) throw fail(c, "one transaction counted twice");
                }
            }
            Map<String, EvidenceItem> merged = new LinkedHashMap<>();
            for (EvidenceItem e : prior) merged.put(key(e), e);
            for (EvidenceItem e : current) merged.putIfAbsent(key(e), e);
            return List.copyOf(new ArrayList<>(merged.values()));
        }

        private static boolean pair(FactCode a, FactCode b) {
            return (a == FactCode.INITIAL_PAYMENT_COMPLETED && b == FactCode.ADDITIONAL_PAYMENT_COMPLETED)
                    || (a == FactCode.INVESTMENT_COMPLETED && b == FactCode.EXTRA_FEE_PAID);
        }

        private static String key(EvidenceItem e) {
            return e.factCode() + "|" + e.roundNo() + "|" + e.speaker() + "|" + e.source() + "|" + e.eventOrder() + "|" + e.quote();
        }
    }
}
