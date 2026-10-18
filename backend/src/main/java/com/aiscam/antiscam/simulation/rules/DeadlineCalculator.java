package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.simulation.domain.SimulationSession;
import java.time.Instant;

/** Effective deadline (DB design §7.9): while paused, elapsed pause time counts up to the remaining 300 s credit. */
public final class DeadlineCalculator {
    public static final long PAUSE_CREDIT_CAP_MS = 300_000;
    private DeadlineCalculator() {}

    public static Instant effectiveDeadline(Instant deadlineAt, Instant pausedAt, int pausedTotalMs, Instant now) {
        if (pausedAt == null) return deadlineAt;
        long credit = Math.max(0, Math.min(now.toEpochMilli() - pausedAt.toEpochMilli(), PAUSE_CREDIT_CAP_MS - pausedTotalMs));
        return deadlineAt.plusMillis(credit);
    }

    public static Instant effectiveDeadline(SimulationSession session, Instant now) {
        return effectiveDeadline(session.getDeadlineAt(), session.getPausedAt(), session.getPausedTotalMs(), now);
    }

    public static boolean isExpired(SimulationSession session, Instant now) {
        return !now.isBefore(effectiveDeadline(session, now));
    }

    /** Earliest instant at which the session could expire; while paused, when the remaining credit runs out. */
    public static Instant nextCheck(SimulationSession session) {
        if (session.getPausedAt() == null) return session.getDeadlineAt();
        return session.getDeadlineAt().plusMillis(PAUSE_CREDIT_CAP_MS - session.getPausedTotalMs());
    }

    public static long remainingMs(SimulationSession session, Instant now) {
        return Math.max(0, effectiveDeadline(session, now).toEpochMilli() - now.toEpochMilli());
    }
}
