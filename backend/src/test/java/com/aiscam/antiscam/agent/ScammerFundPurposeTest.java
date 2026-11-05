package com.aiscam.antiscam.agent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ScammerFundPurposeTest {
    private String output(String purpose) {
        return """
                {"line":"报酬已发放45元","action":"已实际支付45元报酬","intent":"兑现体验报酬",
                 "tone":{"speed":"NORMAL","style":"自然"},"claimedRefundYuan":45,
                 "fundActions":[{"type":"REWARD","purpose":"%s","amountYuan":45,"roundNo":3,
                 "speaker":"SCAMMER","quote":"已实际支付45元报酬","eventOrder":1,
                 "reason":null,"relatedPaymentId":null,"reserveTier":null}]}
                """.formatted(purpose);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMMISSION", "TASK_REBATE", "INITIAL_PAYMENT"})
    void rejectedPurposeFeedbackOnlyOffersScammerPayoutPurposes(String purpose) {
        var error = assertThrows(AgentCallException.class, () -> AgentOutputParser.scammer(output(purpose)));
        assertTrue(error.getMessage().contains("invalid FundPurpose field purpose; allowed values: REFUND,REWARD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REFUND", "REWARD"})
    void preservesLegalPurposeForSubsequentLedgerValidation(String purpose) {
        assertEquals(purpose, AgentOutputParser.scammer(output(purpose)).fundActions().getFirst().purpose().name());
    }
}
