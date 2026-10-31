package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.agent.AgentModels.ReportFundsSummary;
import java.util.List;
import java.util.Map;

public record ReportContext(String template, Map<String, String> variables, String scriptKey, int roundCount,
        String outcomeDisplayText, int contentSchemaVersion, ReportFundsSummary funds,
        Map<Integer, List<String>> quotes, int attemptNo, com.aiscam.antiscam.agent.AgentModels.ReportFundsSummaryV3 fundsV3) {
    public ReportContext(String template, Map<String,String> variables, String scriptKey, int roundCount,
            String outcomeDisplayText, int contentSchemaVersion, ReportFundsSummary funds,
            Map<Integer,List<String>> quotes, int attemptNo) {
        this(template,variables,scriptKey,roundCount,outcomeDisplayText,contentSchemaVersion,funds,quotes,attemptNo,null);
    }
}
