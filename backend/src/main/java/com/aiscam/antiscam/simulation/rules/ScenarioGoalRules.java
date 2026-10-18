package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The three fixed scenario key goals (PRD §10.3 BR-P03); not stored or editable. Order is compared by round
 * number, then by the event order inside one message. No amount threshold; promises or preparation never count.
 */
public final class ScenarioGoalRules {
    private ScenarioGoalRules() {}

    public record Fact(FactCode code, int roundNo, int eventOrder) {}

    private static final Comparator<Fact> ORDER = Comparator.comparingInt(Fact::roundNo).thenComparingInt(Fact::eventOrder);

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
            case TASK_REBATE -> "以完成刷单任务返利为由建立信任，先诱导对方垫付首笔小额资金，再以各种理由诱导追加投入";
            case FAKE_INVESTMENT -> "以投资收益和专业指导建立信任，诱导对方投入资金，再以提现、解冻等理由索取额外费用";
            case IMPERSONATION_LOAN -> "冒充对方的亲友，以紧急情况为由借钱，利用关系信任和时间压力阻止对方通过其他渠道核实";
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
