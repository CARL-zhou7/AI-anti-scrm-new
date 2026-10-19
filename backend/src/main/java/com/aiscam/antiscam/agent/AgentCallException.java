package com.aiscam.antiscam.agent;

/** A model call or output that cannot be used. Retryable failures are retried transparently within budget. */
public class AgentCallException extends RuntimeException {
    public enum Category { DIALOGUE, ANALYSIS, REPORT }

    private final boolean retryable;
    private final Category category;

    public AgentCallException(Category category, boolean retryable, String message) {
        super(message);
        this.category = category;
        this.retryable = retryable;
    }

    public boolean retryable() { return retryable; }
    public Category category() { return category; }
}
