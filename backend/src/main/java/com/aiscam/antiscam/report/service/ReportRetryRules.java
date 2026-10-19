package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.simulation.domain.ReportStatus;
import com.aiscam.antiscam.simulation.domain.SimulationStatus;

public final class ReportRetryRules {
    private ReportRetryRules() {}
    public enum Action { RETURN_EXISTING, REQUEUE, NOT_APPLICABLE, INCONSISTENT }

    public static Action decide(SimulationStatus simulation, ReportStatus report) {
        if (simulation != SimulationStatus.FINISHED) return Action.NOT_APPLICABLE;
        if (report == null) return Action.INCONSISTENT;
        return report == ReportStatus.FAILED ? Action.REQUEUE : Action.RETURN_EXISTING;
    }
}
