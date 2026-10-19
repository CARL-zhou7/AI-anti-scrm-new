package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.agent.AgentModels;
import com.aiscam.antiscam.agent.AgentOutputParser;
import com.aiscam.antiscam.agent.AgentOutputValidator;
import com.aiscam.antiscam.agent.ReportValidationException;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.domain.ReportErrorCode;
import com.aiscam.antiscam.simulation.domain.ReportStatus;
import com.aiscam.antiscam.simulation.domain.SimulationReport;
import static com.aiscam.antiscam.common.time.UtcTime.format;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.*;

public final class ReportViewMapper {
    private ReportViewMapper() {}

    public static ReportSummary summary(SimulationReport row) {
        return new ReportSummary(row.getStatus().name(), row.getStatus().displayText(), row.getStatus().canRetry(),
                row.getAttemptNo(), format(row.getUpdatedAt()),
                row.getStatus() == ReportStatus.FAILED ? errorCode(row.getErrorCode()) : null);
    }

    public static ReportContent content(String json, int rounds, String outcome) {
        try {
            AgentModels.ReportContent c = AgentOutputValidator.report(AgentOutputParser.report(json), rounds, outcome);
            return new ReportContent(c.summary(), c.roundAnalyses().stream()
                    .map(r -> new RoundAnalysis(r.roundNo(), r.analysis())).toList(),
                    c.speechAnalysis(), c.victimBehaviorAnalysis(), c.outcomeAnalysis());
        } catch (ReportValidationException e) {
            throw ApiException.dataInconsistent("记录状态异常，请稍后重试");
        }
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
