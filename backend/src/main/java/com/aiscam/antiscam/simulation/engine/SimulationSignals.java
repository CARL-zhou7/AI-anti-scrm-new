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
}
