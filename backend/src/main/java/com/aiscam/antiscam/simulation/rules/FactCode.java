package com.aiscam.antiscam.simulation.rules;

/**
 * Fixed evidence fact codes (DB design §7.4); not an administrator-editable policy library. Every code describes
 * something the victim did, so evidence may only cite the victim's own line or action.
 */
public enum FactCode {
    INITIAL_PAYMENT_COMPLETED(true),
    ADDITIONAL_PAYMENT_COMPLETED(true),
    INVESTMENT_COMPLETED(true),
    EXTRA_FEE_PAID(true),
    LOAN_TRANSFER_COMPLETED(true),
    VERIFICATION_CODE_GIVEN(true),
    CREDENTIALS_GIVEN(true),
    ACCOUNT_CONTROL_GRANTED(true),
    OTHER_DANGEROUS_ACTION_EXECUTED(true),
    INDEPENDENT_VERIFICATION_COMPLETED(false),
    COOPERATION_STOPPED(false);

    private final boolean actualFraud;
    FactCode(boolean actualFraud) { this.actualFraud = actualFraud; }

    /** Whether the fact is an actually completed defrauding behaviour. */
    public boolean isActualFraud() { return actualFraud; }
}
