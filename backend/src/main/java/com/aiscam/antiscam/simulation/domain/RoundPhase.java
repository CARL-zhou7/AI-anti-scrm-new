package com.aiscam.antiscam.simulation.domain;

public enum RoundPhase {
    SCAMMER_GENERATING, /** Review mode: the generated line waits for the session owner's decision. */
    SCAMMER_REVIEW,
    SCAMMER_PRESENTING, VICTIM_GENERATING, VICTIM_PRESENTING, ANALYZING, ANALYSIS_PRESENTING, COMPLETE
}
