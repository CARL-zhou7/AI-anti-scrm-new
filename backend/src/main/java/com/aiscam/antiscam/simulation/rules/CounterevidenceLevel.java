package com.aiscam.antiscam.simulation.rules;

/**
 * Counterevidence strength for one subject (D8). A subject may upgrade {@code PARTIAL → CONFIRMED} once with new
 * reliable evidence; a repeat at the same level, a downgrade or a retry never deducts again.
 */
public enum CounterevidenceLevel {
    PARTIAL, CONFIRMED;

    public boolean strongerThan(CounterevidenceLevel other) {
        return other == null || rank(this) > rank(other);
    }

    private int rank(CounterevidenceLevel level) {
        return level == CONFIRMED ? 2 : level == PARTIAL ? 1 : 0;
    }
}
