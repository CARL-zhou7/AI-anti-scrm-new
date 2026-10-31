package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.agent.AgentModels;
import com.aiscam.antiscam.agent.AgentOutputParser;
import com.aiscam.antiscam.agent.AgentOutputValidator;
import com.aiscam.antiscam.agent.ReportValidationException;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.domain.ReportErrorCode;
import com.aiscam.antiscam.simulation.domain.ReportStatus;
import com.aiscam.antiscam.simulation.domain.SimulationReport;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import java.util.regex.Pattern;
import static com.aiscam.antiscam.common.time.UtcTime.format;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.*;

public final class ReportViewMapper {
    private static final Pattern AMOUNTS = Pattern.compile("(?<![A-Za-z0-9_])(totalPaidYuan|totalRefundedYuan|actualLossYuan)\\h*(?:[=＝:：]|为)\\h*([0-9]+(?:\\.[0-9]+)?)(?:\\h*元)?");
    private static final Pattern FACTS = Pattern.compile("(?<![A-Za-z0-9_])(actualFraudOccurred|goalAchieved|cooperationStopped|已受骗|目标达成|停止配合)\\h*(?:[=＝:：]|为)\\h*(true|false)(?![A-Za-z0-9_])");
    private ReportViewMapper() {}

    /** Read-only presentation: never rewrite saved reports or transform verbatim evidence quotes. */
    public static String text(String value) {
        if (value == null) return null;
        String result=AMOUNTS.matcher(value).replaceAll(match -> {
            String amount=match.group(2);boolean zero="0".equals(amount);
            return switch(match.group(1)) {
                case "totalPaidYuan" -> zero?"没有实际付款":"累计支付"+amount+"元";
                case "totalRefundedYuan" -> zero?"没有收到返款":"实际收到返款"+amount+"元";
                default -> zero?"没有实际资金损失":"实际亏损"+amount+"元";
            };
        });
        result=FACTS.matcher(result).replaceAll(match -> {
            boolean yes=Boolean.parseBoolean(match.group(2));
            return switch(match.group(1)) {
                case "actualFraudOccurred", "已受骗" -> yes?"已发生实际受骗行为":"未发生实际受骗行为";
                case "goalAchieved", "目标达成" -> yes?"已达到诈骗目标":"尚未达到诈骗目标";
                default -> yes?"已停止配合":"尚未明确停止配合";
            };
        });
        for (var code:OutcomeCode.values()) {
            result=result.replaceAll(Pattern.quote(code.displayText())+"\\h*[（(]\\h*"+code.name()+"\\h*[）)]",code.displayText());
            result=result.replaceAll("(?<![A-Za-z0-9_])"+code.name()+"(?![A-Za-z0-9_])",code.displayText());
        }
        return result.replace("权威资金摘要","资金记录");
    }

    public static ReportSummary summary(SimulationReport row) {
        return new ReportSummary(row.getStatus().name(), row.getStatus().displayText(), row.getStatus().canRetry(),
                row.getAttemptNo(), format(row.getUpdatedAt()),
                row.getStatus() == ReportStatus.FAILED ? errorCode(row.getErrorCode()) : null);
    }

    public static AgentModels.ReportContent content(String json, int rounds, String outcome) {
        try {
            AgentModels.ReportContent c = AgentOutputValidator.report(AgentOutputParser.report(json), rounds, outcome);
            return new AgentModels.ReportContent(text(c.summary()),c.roundAnalyses().stream()
                    .map(round -> new AgentModels.RoundAnalysis(round.roundNo(),text(round.analysis()))).toList(),
                    text(c.speechAnalysis()),text(c.victimBehaviorAnalysis()),text(c.outcomeAnalysis()));
        } catch (ReportValidationException e) {
            throw ApiException.dataInconsistent("记录状态异常，请稍后重试");
        }
    }

    public static AgentModels.ReportContentV2 contentV2(String json, String outcome, AgentModels.ReportFundsSummary funds,
            java.util.Map<Integer, java.util.List<String>> quotes) {
        try {
            var c=AgentOutputValidator.reportV2(AgentOutputParser.reportV2(json),outcome,funds,quotes);
            return new AgentModels.ReportContentV2(c.contentSchemaVersion(),
                    new AgentModels.ReportResult(text(c.result().text()),text(c.result().reason())),text(c.summary()),
                    c.keyNodes().stream().map(node -> new AgentModels.ReportKeyNode(node.roundNo(),node.quote(),text(node.analysis()),text(node.title()))).toList(),
                    c.preventionAdvice().stream().map(ReportViewMapper::text).toList(),c.fundsSummary());
        }
        catch (ReportValidationException e) { throw ApiException.dataInconsistent("记录状态异常，请稍后重试"); }
    }

    public static AgentModels.ReportContentV3 contentV3(String json, String outcome, AgentModels.ReportFundsSummaryV3 funds,
            java.util.Map<Integer, java.util.List<String>> quotes) {
        try {
            var c=AgentOutputValidator.reportV3(AgentOutputParser.reportV3(json),outcome,funds,quotes);
            return new AgentModels.ReportContentV3(c.contentSchemaVersion(),
                    new AgentModels.ReportResult(text(c.result().text()),text(c.result().reason())),text(c.summary()),
                    c.keyNodes().stream().map(node -> new AgentModels.ReportKeyNode(node.roundNo(),node.quote(),text(node.analysis()),text(node.title()))).toList(),
                    c.preventionAdvice().stream().map(ReportViewMapper::text).toList(),c.fundsSummary());
        }
        catch (ReportValidationException e) { throw ApiException.dataInconsistent("记录状态异常，请稍后重试"); }
    }

    /**
     * V1.4 report: parse the stored v3 structure, validate it with the v4 wording rules, then inject the saved
     * verdict. The model's own winning/final fields are never trusted (and never parsed).
     */
    public static AgentModels.ReportContentV4 contentV4(String json, String outcome, AgentModels.ReportFundsSummaryV3 funds,
            java.util.Map<Integer, java.util.List<String>> quotes, String winning, Integer finalTrustScore) {
        try {
            var c=AgentOutputValidator.reportV4(AgentOutputParser.reportV3(json),outcome,funds,quotes);
            return new AgentModels.ReportContentV4(4,
                    new AgentModels.ReportResult(text(c.result().text()),text(c.result().reason())),winning,finalTrustScore,
                    text(c.summary()),
                    c.keyNodes().stream().map(node -> new AgentModels.ReportKeyNode(node.roundNo(),node.quote(),text(node.analysis()),text(node.title()))).toList(),
                    c.preventionAdvice().stream().map(ReportViewMapper::text).toList(),c.fundsSummary());
        }
        catch (ReportValidationException e) { throw ApiException.dataInconsistent("记录状态异常，请稍后重试"); }
    }

    public static ReportError errorCode(String code) {
        try {
            var error = ReportErrorCode.valueOf(code);
            return new ReportError(error.name(), error.message());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.dataInconsistent("记录状态异常，请稍后重试");
        }
    }
}
