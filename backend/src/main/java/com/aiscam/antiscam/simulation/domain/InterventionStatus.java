package com.aiscam.antiscam.simulation.domain;

/**
 * Lifecycle of an adopted intervention (option A).
 * <ul>
 *   <li>{@code PENDING} — the formal message does not exist yet (transient during adoption).</li>
 *   <li>{@code ADOPTED} — adopted and waiting for the knowledge base; only these records may become rule sources.</li>
 *   <li>{@code LINKED} — already a source of at least one scene rule.</li>
 * </ul>
 */
public enum InterventionStatus {
    PENDING, ADOPTED, LINKED;

    /** Waiting for the knowledge base: adopted but not used by any rule yet. */
    public boolean awaitingKnowledgeBase() { return this == ADOPTED; }
}
