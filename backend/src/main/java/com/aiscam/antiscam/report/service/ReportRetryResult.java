package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.simulation.web.dto.SimulationViews.ReportView;

public record ReportRetryResult(int httpStatus, ReportView view) {}
