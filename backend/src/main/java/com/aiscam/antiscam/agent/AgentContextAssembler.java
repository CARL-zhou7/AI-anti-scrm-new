package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.ScenarioGoalRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds each role's prompt variables from only what that role may see (plan Task 6 isolation table):
 * the scammer and the victim never receive internal notes, narration or risk; the victim never receives the
 * scenario type or key-goal definition; only the narrator sees internal notes.
 */
public final class AgentContextAssembler {
    public static final int LINE_TARGET_CHARS = 50;
    public static final int ANALYSIS_TARGET_CHARS = 40;
    private AgentContextAssembler() {}

    public static Map<String, String> scammer(AgentScene scene, List<DialogueLine> history, int roundNo, long remainingSeconds) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("line_target_chars", Integer.toString(LINE_TARGET_CHARS));
        v.put("scenario_name", scene.scenarioName());
        v.put("scenario_description", scene.scenarioDescription());
        v.put("scenario_goal_hint", ScenarioGoalRules.scammerHint(scene.scenarioCode()));
        v.put("scammer_profile", scene.scammerProfile());
        v.put("dialogue_history", publicHistory(history));
        v.put("round_no", Integer.toString(roundNo));
        v.put("remaining_seconds", Long.toString(remainingSeconds));
        String pacing = roundNo <= 4 ? "当前处于前4轮，以建立联系、建立信任和铺垫为主，不要急于收网。"
                : "可以根据对方状态加大推进力度。";
        if (remainingSeconds < 120) pacing += "剩余时间较少，台词请更简短。";
        v.put("pacing_hint", pacing);
        return v;
    }

    public static Map<String, String> victim(AgentScene scene, List<DialogueLine> history, DialogueLine scammerLine,
            int roundNo, long remainingSeconds, boolean regeneration) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("line_target_chars", Integer.toString(LINE_TARGET_CHARS));
        v.put("minimum_round_constraint", roundNo <= 4
                ? "【前4轮行为约束】当前为第" + roundNo + "轮（前4轮）。你可以怀疑、质疑、拖延、表示要核实或着手核实，也可以部分配合；"
                  + "但本轮不要作出明确终止配合（如拉黑、挂断后不再联系、明确拒绝继续）或彻底完成对方全部要求等决定性行为。"
                : "");
        v.put("regeneration_note", regeneration
                ? "【重新生成提示】你上一版回应会形成决定性的结束行为，已被退回。请保持人物性格，给出一个尚未作出决定性行为的回应。"
                : "");
        v.put("victim_profile", scene.victimProfile());
        v.put("dialogue_history", publicHistory(history));
        v.put("scammer_line", publicLine(scammerLine));
        v.put("round_no", Integer.toString(roundNo));
        v.put("remaining_seconds", Long.toString(remainingSeconds));
        return v;
    }

    /** @param lines all lines up to and including the current round, internal notes included (narrator only). */
    public static Map<String, String> narrator(AgentScene scene, List<DialogueLine> lines, int roundNo, PriorFacts prior) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("scenario_name", scene.scenarioName());
        v.put("scenario_code", scene.scenarioCode().name());
        v.put("scenario_goal", ScenarioGoalRules.description(scene.scenarioCode()));
        v.put("scammer_profile", scene.scammerProfile());
        v.put("victim_profile", scene.victimProfile());
        v.put("dialogue_history", fullHistory(lines.stream().filter(l -> l.roundNo() < roundNo).toList()));
        v.put("round_no", Integer.toString(roundNo));
        v.put("current_round", fullHistory(lines.stream().filter(l -> l.roundNo() == roundNo).toList()));
        v.put("previous_facts", facts(prior));
        v.put("analysis_target_chars", Integer.toString(ANALYSIS_TARGET_CHARS));
        return v;
    }

    public static String victimFunds(FundsContext f) {
        return "当前可动用余额：" + f.availableBalanceYuan() + "元；尚可释放预留：" + f.releasableReserveYuan()
                + "元。只记录本轮新执行付款/预留释放，不将旧转账复述作为新动作。";
    }

    public static String scammerFunds(FundsContext f) {
        String payments = f.existingFlows().stream().filter(p -> p.getFlowType() == FundFlowType.PAYMENT)
                .map(p -> {
                    int refunded = f.existingFlows().stream().filter(r -> java.util.Objects.equals(r.getRelatedPaymentId(), p.getId()))
                            .mapToInt(FundFlow::getAmountYuan).sum();
                    return "付款ID=" + p.getId() + "，收款=" + p.getAmountYuan() + "元，尚可关联返款=" + (p.getAmountYuan() - refunded) + "元";
                }).collect(Collectors.joining("\n"));
        return "已收款：" + f.totalPaidYuan() + "元；已实际返款：" + f.totalRefundedYuan()
                + "元；剩余可返额度：" + Math.max(0, Math.min(f.refundCapYuan(), f.totalPaidYuan()) - f.totalRefundedYuan())
                + "元。" + (f.refundCapYuan() == 0 ? "本场禁止实际返款，claimedRefundYuan必须为null。" : "声称不入账，实际返款不得超过声称。") + "\n" + payments;
    }

    public static String narratorFunds(FundsContext f, Integer claimed, boolean candidate) {
        String flows = f.existingFlows().stream().map(p -> "顺序" + p.getSequenceNo() + " " + p.getFlowType()
                + " " + p.getAmountYuan() + "元，发生后余额" + p.getBalanceAfterYuan() + "元").collect(Collectors.joining("\n"));
        return (candidate ? "候选预检：本轮受害者行为尚未保存，只作最低轮数筛查，不作为已入账事实。\n" : "正式分析，仅以下流水为已入账事实。\n")
                + "本轮骗子声称返款：" + (claimed == null ? "未提供" : claimed + "元（不是实际到账）")
                + "\n当前余额：" + f.availableBalanceYuan() + "元\n" + flows;
    }

    private static String publicHistory(List<DialogueLine> lines) {
        if (lines.isEmpty()) return "（暂无，这是第一轮）";
        return lines.stream().map(AgentContextAssembler::publicLine).collect(Collectors.joining("\n"));
    }

    private static String publicLine(DialogueLine l) {
        return "第" + l.roundNo() + "轮 " + who(l.speaker()) + "：" + l.content()
                + (l.action() == null ? "" : "\n    （行动：" + l.action() + "）");
    }

    private static String fullHistory(List<DialogueLine> lines) {
        if (lines.isEmpty()) return "（暂无）";
        return lines.stream().map(l -> publicLine(l) + "\n    ［" + (l.speaker() == SpeakerRole.SCAMMER ? "内部意图" : "内部心理")
                + "：" + (l.internalNote() == null ? "无" : l.internalNote()) + "］").collect(Collectors.joining("\n"));
    }

    private static String facts(PriorFacts prior) {
        StringBuilder sb = new StringBuilder();
        sb.append("已发生实际受骗行为：").append(prior.actualFraud() ? "是" : "否")
                .append("；已达成场景关键目标：").append(prior.goalAchieved() ? "是" : "否").append("\n证据：");
        if (prior.evidence().isEmpty()) return sb.append("无").toString();
        for (EvidenceItem e : prior.evidence()) {
            sb.append("\n- ").append(e.factCode()).append(" 第").append(e.roundNo()).append("轮 ").append(e.speaker())
                    .append(' ').append(e.source()).append(" 「").append(e.quote()).append("」 eventOrder=").append(e.eventOrder());
        }
        return sb.toString();
    }

    private static String who(SpeakerRole role) { return role == SpeakerRole.SCAMMER ? "骗子" : "受害者"; }
}
