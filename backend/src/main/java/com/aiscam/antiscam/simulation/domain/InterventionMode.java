package com.aiscam.antiscam.simulation.domain;

/**
 * Frozen at start: NONE preserves automatic sessions; ADMIN_TEST enables the review gate.
 * During private testing both login roles use ADMIN_TEST, retaining the existing stored enum value.
 */
public enum InterventionMode {
    NONE, ADMIN_TEST
}
