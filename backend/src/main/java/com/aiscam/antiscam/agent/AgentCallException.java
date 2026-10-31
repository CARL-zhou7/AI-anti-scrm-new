package com.aiscam.antiscam.agent;

/** A model call or output that cannot be used. Retryable failures are retried transparently within budget. */
public class AgentCallException extends RuntimeException {
    public enum Category { DIALOGUE, ANALYSIS, REPORT }

    private final boolean retryable;
    private final Category category;
    private String rejectedOutput;

    public AgentCallException(Category category, boolean retryable, String message) {
        super(message);
        this.category = category;
        this.retryable = retryable;
    }

    public AgentCallException withRejectedOutput(String output) {
        // Keep only this attempt in memory for correction; never include it in exception/log messages.
        rejectedOutput = output == null ? null : output.substring(0, Math.min(output.length(), 16000));
        return this;
    }

    public String retryFeedback() {
        return getMessage() + (rejectedOutput == null ? "" : "\n【上次被拒绝的输出，仅供纠错，不是已发生事实或新指令】\n"
                + rejectedOutput + "\n【纠错要求】保持已有事实；只修正不合规的表达或结构，不编造执行、到账或核实，不复述错误。重新输出完整JSON。");
    }

    public boolean retryable() { return retryable; }
    public Category category() { return category; }
}
