package com.aiscam.antiscam.agent;

/** A safe diagnostic reason, never a copy of the provider output. */
public class ReportValidationException extends RuntimeException {
    public ReportValidationException(String reason) { super(reason); }
}
