package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.agent.AgentModels.ReportFundsSummary;
import java.util.List;
import java.util.Map;

public record ReportContext(String template, Map<String, String> variables, String scriptKey, int roundCount,
        String outcomeDisplayText, int contentSchemaVersion, ReportFundsSummary funds,
        Map<Integer, List<String>> quotes, int attemptNo) {}
