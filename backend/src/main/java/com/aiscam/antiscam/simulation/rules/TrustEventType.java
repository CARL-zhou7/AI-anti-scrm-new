package com.aiscam.antiscam.simulation.rules;

public enum TrustEventType {
    NEW_EFFECTIVE_EXPLANATION(3), PROMISE_FULFILLED(5), NEW_CONTRADICTION(-5),
    DECISIVE_COUNTEREVIDENCE(-10), INDEPENDENT_SUPPORT(1), INDEPENDENT_DOUBT(-1), REPEAT_NO_PROGRESS(0),
    DOUBT_RESOLVED(4), PROFESSIONAL_EXPLANATION(3), RELEVANT_MATERIAL(4), EVASIVE_RESPONSE(-1),
    /** V1.4: releases one counterevidence subject; never scores on its own. */
    COUNTEREVIDENCE_RESOLVED(0);

    private final int baseDelta;
    TrustEventType(int baseDelta) { this.baseDelta = baseDelta; }
    public int baseDelta() { return baseDelta; }
    public boolean positiveExplanation() {
        return this == NEW_EFFECTIVE_EXPLANATION || this == DOUBT_RESOLVED
                || this == PROFESSIONAL_EXPLANATION || this == RELEVANT_MATERIAL;
    }
    public boolean v2Only() {
        return this == DOUBT_RESOLVED || this == PROFESSIONAL_EXPLANATION
                || this == RELEVANT_MATERIAL || this == EVASIVE_RESPONSE;
    }

    /** V1.4 (trust-v3) base values; the legacy {@link #baseDelta()} stays exactly as shipped for old sessions. */
    public int baseDeltaV3() {
        return switch (this) {
            case NEW_EFFECTIVE_EXPLANATION, PROFESSIONAL_EXPLANATION -> 3;
            case DOUBT_RESOLVED, RELEVANT_MATERIAL -> 4;
            case PROMISE_FULFILLED -> 5;
            case INDEPENDENT_SUPPORT -> 2;
            case INDEPENDENT_DOUBT, EVASIVE_RESPONSE -> -3;
            case NEW_CONTRADICTION -> -6;
            case REPEAT_NO_PROGRESS, DECISIVE_COUNTEREVIDENCE, COUNTEREVIDENCE_RESOLVED -> 0;
        };
    }

    /** Types that add trust and are therefore blocked while any counterevidence subject is still active. */
    public boolean positiveInV3() { return baseDeltaV3() > 0; }

    public boolean counterevidenceInV3() {
        return this == DECISIVE_COUNTEREVIDENCE || this == COUNTEREVIDENCE_RESOLVED;
    }

    /** Event types only trust-v3 sessions may emit. */
    public boolean v3Only() { return this == COUNTEREVIDENCE_RESOLVED; }
}
