package com.aiscam.antiscam.agent;
public final class FundsUnavailableException extends AgentCallException {
    public FundsUnavailableException() { super(Category.DIALOGUE, false, "funds exhausted"); }
}
