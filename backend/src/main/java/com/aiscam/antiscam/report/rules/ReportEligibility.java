package com.aiscam.antiscam.report.rules;

import com.aiscam.antiscam.simulation.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ReportEligibility {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ReportEligibility() {}
    public static boolean isV34(SimulationSession session) {
        return "PRD-V3.4".equals(ruleset(session));
    }

    public static boolean isV35(SimulationSession session) {
        return "PRD-V3.5".equals(ruleset(session));
    }

    /** Sessions whose business endings may produce a report: PRD-V3.4 keeps its old endings, PRD-V3.5 exits too. */
    public static boolean supportsBusinessReport(SimulationSession session) {
        return isV34(session) || isV35(session);
    }

    private static String ruleset(SimulationSession session) {
        String runtime=session.getRuntimeSnapshot();
        if (runtime==null) return null;
        try { return JSON.readTree(runtime).path("ruleset_version").asText(); }
        catch (java.io.IOException e) { throw new IllegalStateException("Unreadable runtime snapshot",e); }
    }

    public static boolean isBusinessEnd(SimulationSession session) {
        if (session.getStatus()!=SimulationStatus.INTERRUPTED) return false;
        var code=session.getInterruptionCode();
        // V1.4 only has a user exit; funds exhaustion and character exit no longer terminate a session.
        if (isV35(session)) return code==InterruptionCode.USER_EXIT;
        return code==InterruptionCode.USER_EXIT || code==InterruptionCode.FUNDS_EXHAUSTED || code==InterruptionCode.CHARACTER_EXIT;
    }
    public static boolean isEligible(SimulationSession session, long completeRounds) {
        return session.getStatus()==SimulationStatus.FINISHED
                || isBusinessEnd(session) && supportsBusinessReport(session) && completeRounds>0;
    }
    public static boolean isLegacyExit(SimulationSession session) { return isBusinessEnd(session) && !supportsBusinessReport(session); }
    public static String endingReason(SimulationSession session) {
        return session.getInterruptionCode().message()+"；未形成受骗或识破结论。";
    }
}
