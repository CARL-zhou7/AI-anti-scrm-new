package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.simulation.domain.SimulationSession;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Version gate for the 15-round V1.4 ruleset (PRD-V3.5). Only the frozen {@code runtime_snapshot} decides, so a
 * session always keeps the rules it started with; {@code trust-v2}/{@code PRD-V3.4} runs stay on 20 rounds.
 */
public final class SimulationRuleset {
    public static final String RULESET_V34 = "PRD-V3.4";
    public static final String RULESET_V35 = "PRD-V3.5";
    public static final int MAX_ROUNDS_V35 = 15;
    public static final int MAX_ROUNDS_V34 = 20;
    private static final ObjectMapper JSON = new ObjectMapper();

    private SimulationRuleset() {}

    public static boolean isV35(SimulationSession session) {
        String runtime = session.getRuntimeSnapshot();
        if (runtime == null) return false;
        try {
            return RULESET_V35.equals(JSON.readTree(runtime).path("ruleset_version").asText());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unreadable runtime snapshot", e);
        }
    }

    public static int maxRounds(SimulationSession session) {
        return isV35(session) ? MAX_ROUNDS_V35 : MAX_ROUNDS_V34;
    }

    public static boolean isFinalRound(SimulationSession session, int roundNo) {
        return roundNo == maxRounds(session);
    }
}
