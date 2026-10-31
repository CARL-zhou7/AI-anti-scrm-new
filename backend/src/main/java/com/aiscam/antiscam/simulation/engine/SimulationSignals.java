package com.aiscam.antiscam.simulation.engine;

/** After-commit notifications from the transaction layer to the orchestration engine. */
public interface SimulationSignals {
    /** A new RUNNING session was committed. */
    void started(long sessionId);

    /** Persistent state moved (round created, receipt applied, resumed...); the engine re-evaluates its next step. */
    void changed(long sessionId);

    /** Pause state changed; the engine reschedules its timers. */
    void pauseChanged(long sessionId);

    /** The session reached FINISHED or INTERRUPTED; prepared content must be discarded and audio released. */
    void terminated(long sessionId, boolean interrupted);

    /**
     * An administrator adopted a reviewed draft: the formal line is already saved and now needs its media step and
     * the victim's next turn. Defaults to a plain {@link #changed(long)} for implementations without a review gate.
     */
    default void draftAdopted(long sessionId, int roundNo) { changed(sessionId); }

    /**
     * A draft was discarded for regeneration: the prepared but unpublished line must be dropped so the engine asks
     * the model for a new one instead of re-submitting the same text.
     */
    default void draftDiscarded(long sessionId, int roundNo, String guidance) { changed(sessionId); }
}
