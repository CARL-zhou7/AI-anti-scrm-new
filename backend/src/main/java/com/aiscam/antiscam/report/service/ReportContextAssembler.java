package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Only frozen snapshots and persisted facts may enter this context; never log the result. */
public final class ReportContextAssembler {
    private ReportContextAssembler() {}

    public static String outcomeText(SimulationSession session, List<SimulationRound> rounds) {
        if (session.getOutcomeCode() == null) return "未形成受骗或识破结论";
        if (!ConfigurationSnapshots.trustV2(session)) return session.getOutcomeCode().displayText();
        var last = rounds.stream().filter(SimulationRound::hasAnalysis).max(Comparator.comparingInt(SimulationRound::getRoundNo)).orElse(null);
        return com.aiscam.antiscam.simulation.rules.OutcomeRules.displayV2(session.getOutcomeCode(),
                last != null && last.isGoalAchieved(), last != null && last.isActualFraudOccurred() && last.isCooperationStopped());
    }

    public static Map<Integer, List<String>> quotes(List<SimulationRound> rounds, Map<Long, List<SimulationMessage>> messages) {
        var result = new java.util.LinkedHashMap<Integer, List<String>>();
        for (var round : rounds) result.put(round.getRoundNo(), messages.getOrDefault(round.getId(), List.of()).stream()
                .flatMap(m -> java.util.stream.Stream.of(m.getContent(),m.getActionText())).filter(java.util.Objects::nonNull).toList());
        return Map.copyOf(result);
    }

    public static Map<String, String> variablesV2(SimulationSession session, List<SimulationRound> rounds,
            Map<Long, List<SimulationMessage>> messages, ConfigurationSnapshots snapshots, List<FundFlow> flows) {
        var values = new java.util.LinkedHashMap<>(variables(session,rounds,messages,snapshots));
        var fundText = new StringBuilder("资金记录：累计支付").append(session.getTotalPaidYuan())
                .append("元，实际返款").append(session.getTotalRefundedYuan())
                .append("元，实际亏损").append(session.getActualLossYuan()).append("元\n");
        for (var flow : flows) fundText.append("顺序").append(flow.getSequenceNo()).append(' ')
                .append(switch(flow.getPurpose()) {
                    case INITIAL_PAYMENT -> "首笔付款"; case ADDITIONAL_PAYMENT -> "追加付款";
                    case INVESTMENT_PAYMENT -> "投资款"; case EXTRA_FEE_PAYMENT -> "额外费用";
                    case LOAN_TRANSFER -> "借款转账"; case OTHER_PAYMENT -> "其他付款";
                    case REWARD -> "实际报酬"; case REFUND -> "实际返款"; case RESERVE_RELEASE -> "预留释放";
                }).append(' ')
                .append(flow.getAmountYuan()).append("元，余额").append(flow.getBalanceAfterYuan()).append("元\n");
        fundText.append("报告正文请用自然中文描述事实，不照抄字段名、枚举代码或布尔值；JSON键仍按要求保留。");
        values.put("funds", fundText.toString());
        values.put("key_facts", rounds.stream().filter(SimulationRound::hasAnalysis).map(r -> "第"+r.getRoundNo()
                +"轮："+(r.isActualFraudOccurred()?"已发生实际受骗行为":"未发生实际受骗行为")
                +"，"+(r.isGoalAchieved()?"已达到诈骗目标":"尚未达到诈骗目标")
                +"，"+(r.isCooperationStopped()?"已停止配合":"尚未明确停止配合"))
                .collect(java.util.stream.Collectors.joining("\n")));
        return values;
    }

    public static Map<String,String> variablesV3(SimulationSession session, List<SimulationRound> rounds,
            Map<Long,List<SimulationMessage>> messages, ConfigurationSnapshots snapshots, List<FundFlow> flows) {
        var values = new java.util.LinkedHashMap<>(variablesV2(session,rounds,messages,snapshots,flows));
        var f = com.aiscam.antiscam.agent.AgentModels.ReportFundsSummaryV3.from(session);
        values.put("funds", "totalPaidYuan="+f.totalPaidYuan()+"\ntotalRefundedYuan="+f.totalRefundedYuan()
                +"\ntotalRewardedYuan="+f.totalRewardedYuan()+"\nactualLossYuan="+f.actualLossYuan()
                +"\navailableBalanceYuan="+f.availableBalanceYuan()+"\ngeneralReserveRemainingYuan="+f.generalReserveRemainingYuan()
                +"\nemergencyReserveRemainingYuan="+f.emergencyReserveRemainingYuan()+"\n"+values.get("funds"));
        return values;
    }

    public static Map<String, String> variables(SimulationSession session, List<SimulationRound> rounds,
            Map<Long, List<SimulationMessage>> messagesByRound, ConfigurationSnapshots snapshots) {
        var scenario = snapshots.read(session.getScenarioSnapshot());
        String outcome = outcomeText(session, rounds) + "：" + ReportViewMapper.text(session.getOutcomeCode()==null
                        ? com.aiscam.antiscam.report.rules.ReportEligibility.endingReason(session) : session.getOutcomeReason())
                + (session.getLimitReason() == null ? "" : "；" + switch(session.getLimitReason()) {
                    case ROUND_LIMIT -> "达到轮数上限"; case TIME_LIMIT -> "达到时长上限"; case BOTH -> "同时达到轮数和时长上限";
                });
        var text = new StringBuilder();
        for (var round : rounds.stream().sorted(Comparator.comparingInt(SimulationRound::getRoundNo)).toList()) {
            text.append("第").append(round.getRoundNo()).append("轮｜");
            for (var m : messagesByRound.getOrDefault(round.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(SimulationMessage::getSpeakerRole)).toList()) {
                boolean scammer = m.getSpeakerRole() == SpeakerRole.SCAMMER;
                text.append(scammer ? "骗子：" : "受害者：").append(m.getContent()).append("（");
                if (m.getActionText() != null) text.append("行动：").append(m.getActionText()).append("；");
                text.append(scammer ? "内部意图：" : "内部心理：").append(m.internalNoteForAgents()).append("）／");
            }
            if (round.hasAnalysis()) {
                text.append("解说：阶段：").append(round.getFraudStage()).append("；话术：").append(round.getSpeechAnalysis())
                        .append("；手段：").append(round.getMethodAnalysis()).append("；心理：").append(round.getVictimPsychology())
                        .append("；风险分：").append(round.getRiskScore()).append("；依据：").append(round.getRiskReason());
            } else text.append("本轮无解说");
            text.append('\n');
        }
        return Map.of("scenario", scenario.path("name").asText() + "：" + scenario.path("description").asText(),
                "profiles", "骗子：\n" + snapshots.scammerText(session.getScammerSnapshot()) + "\n受害者：\n"
                        + snapshots.victimText(session.getVictimSnapshot()), "outcome", outcome, "rounds", text.toString());
    }
}
