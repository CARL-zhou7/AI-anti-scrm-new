package com.aiscam.antiscam.simulation.domain;

public record FundAction(FundFlowType type, FundPurpose purpose, int amountYuan, int roundNo,
        SpeakerRole speaker, String quote, int eventOrder, String reason, Long relatedPaymentId, FundReserveTier reserveTier) {
    public FundAction(FundFlowType type, FundPurpose purpose, int amountYuan, int roundNo, SpeakerRole speaker,
            String quote, int eventOrder, String reason, Long relatedPaymentId) {
        this(type, purpose, amountYuan, roundNo, speaker, quote, eventOrder, reason, relatedPaymentId, null);
    }
}
