package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.FundsContext;
import com.aiscam.antiscam.simulation.domain.FundPosting;
import com.aiscam.antiscam.simulation.domain.FundFlowType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Legacy scenario goals and the trust-v2 small-to-large payment goal. Only executed, validated funds count.
 */
public final class ScenarioGoalRules {
    private ScenarioGoalRules() {}

    public record Fact(FactCode code, int roundNo, int eventOrder) {}

    private static final Comparator<Fact> ORDER = Comparator.comparingInt(Fact::roundNo).thenComparingInt(Fact::eventOrder);

    /** Replays validated postings; rewards never reduce unrecovered principal. */
    public static boolean goalAchieved(FundsContext before, List<FundPosting> candidatePostings) {
        var postings = new java.util.ArrayList<FundPosting>();
        var rounds = new java.util.HashMap<Integer, Long>();
        for (var flow : before.existingFlows()) {
            rounds.put(flow.getSequenceNo(), flow.getRoundId());
            postings.add(new FundPosting(
                flow.getFlowType(), flow.getPurpose(), flow.getAmountYuan(), flow.getSequenceNo(),
                flow.getBalanceAfterYuan(), flow.getReason(), flow.getRelatedPaymentId()));
        }
        postings.addAll(candidatePostings);
        postings.sort(Comparator.comparingInt(FundPosting::sequenceNo));
        long unrecovered = 0;
        Long smallRound = null;
        int cap = before.gate().smallCapYuan();
        for (var posting : postings) {
            if (posting.type() == FundFlowType.REFUND) unrecovered -= posting.amountYuan();
            if (posting.type() != FundFlowType.PAYMENT) continue;
            unrecovered += posting.amountYuan();
            long round = rounds.getOrDefault(posting.sequenceNo(), Long.MAX_VALUE);
            if (smallRound != null && smallRound != round && unrecovered > cap) return true;
            if (smallRound == null && posting.amountYuan() <= cap && unrecovered <= cap) smallRound = round;
        }
        return false;
    }

    public static boolean goalAchieved(ScenarioCode scenario, List<Fact> facts) {
        return switch (scenario) {
            case TASK_REBATE -> ordered(facts, FactCode.INITIAL_PAYMENT_COMPLETED, FactCode.ADDITIONAL_PAYMENT_COMPLETED);
            case FAKE_INVESTMENT -> ordered(facts, FactCode.INVESTMENT_COMPLETED, FactCode.EXTRA_FEE_PAID);
            case IMPERSONATION_LOAN -> {
                Optional<Fact> transfer = first(facts, FactCode.LOAN_TRANSFER_COMPLETED);
                yield transfer.isPresent() && facts.stream().noneMatch(f -> f.code() == FactCode.INDEPENDENT_VERIFICATION_COMPLETED
                        && ORDER.compare(f, transfer.get()) < 0);
            }
        };
    }

    public static boolean goalAchieved(ScenarioCode scenario, List<Fact> facts,
            com.aiscam.antiscam.simulation.domain.FundsSummary funds) {
        if (!goalAchieved(scenario, facts) || funds.totalPaidYuan() == 0) return false;
        if (scenario == ScenarioCode.FAKE_INVESTMENT && funds.additionalPaymentCount() < 1) return false;
        return scenario != ScenarioCode.TASK_REBATE || (funds.additionalPaymentCount() >= 1
                && (funds.additionalPaymentCount() >= 3 || funds.actualLossYuan() >= 1000));
    }

    public static String description(ScenarioCode scenario, boolean v12) {
        return v12 && scenario == ScenarioCode.TASK_REBATE
                ? "首笔垫付后至少追加1次，且追加累计3次或累计净亏损达到1000元；已形成结果不回退"
                : description(scenario);
    }

    /** Goal description given to the narrator prompt; deliberately never shown to the victim role. */
    public static String description(ScenarioCode scenario) {
        return switch (scenario) {
            case TASK_REBATE -> "受害者已完成首笔垫付后，又实际完成一次追加投入（承诺或准备不算完成）";
            case FAKE_INVESTMENT -> "受害者已投入投资款后，又实际支付一次所谓提现、解冻等额外费用（承诺或准备不算完成）";
            case IMPERSONATION_LOAN -> "受害者未通过独立渠道核实对方身份，就实际完成借款转账（承诺或准备不算完成）";
        };
    }

    /** Push direction for the scammer prompt (no evaluation criteria). */
    public static String scammerHint(ScenarioCode scenario) {
        return switch (scenario) {
            case TASK_REBATE -> "以完成刷单任务返利为由建立信任，先诱导对方垫付首笔小额资金，再以各种理由诱导追加投入。任务、订单、佣金和垫付属于本场语境，不套用投资认购或亲友借款流程；报酬金额随当前情境决定，不固定照抄示例";
            case FAKE_INVESTMENT -> "以投资收益和专业指导建立信任，诱导对方投入资金，再以提现、解冻等理由索取额外费用。始终围绕投资项目、出资、收益与提现展开，不转为刷单、接单、完成任务拿佣金或首单免垫付。小额体验只能表述为投资体验、收益验证或提现验证，不能按完成任务发工资；未经出资不得虚构已产生投资收益。对方追问流程时解释投资流程，不把投资改成兼职任务";
            case IMPERSONATION_LOAN -> "冒充对方的亲友，以紧急情况为由借钱，利用关系信任和时间压力阻止对方通过其他渠道核实。保持亲友借款语境，不引入刷单任务、佣金报酬或投资收益，也不通过小额返利建立信任；沿用首次明确的借款主事件，追加金额须属于同一事件的合理费用，不从合同或医疗借款跳到网络借贷、还贷催收、扣押证件或绑架赎金，不让受害者注册借贷平台或借网贷";
        };
    }

    private static boolean ordered(List<Fact> facts, FactCode first, FactCode second) {
        Optional<Fact> a = first(facts, first);
        return a.isPresent() && facts.stream().anyMatch(f -> f.code() == second && ORDER.compare(f, a.get()) > 0);
    }

    private static Optional<Fact> first(List<Fact> facts, FactCode code) {
        return facts.stream().filter(f -> f.code() == code).min(ORDER);
    }
}
