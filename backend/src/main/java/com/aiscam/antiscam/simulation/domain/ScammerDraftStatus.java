package com.aiscam.antiscam.simulation.domain;

/** Lifecycle of one administrator-facing scammer draft. Only {@code DRAFT} occupies the round's active slot. */
public enum ScammerDraftStatus {
    DRAFT, ADOPTED, DISCARDED
}
