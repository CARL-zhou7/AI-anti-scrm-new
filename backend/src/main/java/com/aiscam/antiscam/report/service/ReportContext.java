package com.aiscam.antiscam.report.service;

import java.util.Map;

public record ReportContext(String template, Map<String, String> variables, String scriptKey, int roundCount,
        String outcomeDisplayText) {}
