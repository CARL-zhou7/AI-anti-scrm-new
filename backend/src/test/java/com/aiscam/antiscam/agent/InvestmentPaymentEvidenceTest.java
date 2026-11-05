package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.DialogueLine;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class InvestmentPaymentEvidenceTest {
    private final FundsContext funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT, 3000, 0, 0, 0, 0, List.of());

    private void validate(String source, String quote, List<DialogueLine> history) {
        var payment = new FundAction(FundFlowType.PAYMENT, FundPurpose.INVESTMENT_PAYMENT,
                80, 10, SpeakerRole.VICTIM, quote, 1, null, null);
        AgentOutputValidator.funds(SpeakerRole.VICTIM, "按这笔安排操作。", source, null,
                List.of(payment), 10, funds, history);
    }

    @ParameterizedTest
    @ValueSource(strings = {"已实际充值80元到投资平台账户", "已完成80元入金", "已投入80元到投资平台账户", "充值了80元到投资平台账户"})
    void recognizesCompletedInvestmentPayments(String text) {
        assertDoesNotThrow(() -> validate(text, text, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"准备充值80元", "已决定充值80元", "已计划投入80元", "尚未实际充值80元", "完成任务后实际充值80元"})
    void rejectsPlansAndSelectiveQuotes(String source) {
        String quote = source.contains("实际充值") ? "实际充值80元" : source;
        assertThrows(AgentCallException.class, () -> validate(source, quote, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"已实际转账80元", "本轮再次已实际转账80元"})
    void distinguishesReplayedEvidenceFromExplicitNewExecution(String quote) {
        var history = List.of(new DialogueLine(9, SpeakerRole.VICTIM, "已经操作。", "已实际转账80元", null));
        if (quote.startsWith("本轮")) assertDoesNotThrow(() -> validate(quote, quote, history));
        else {
            var error = assertThrows(AgentCallException.class, () -> validate(quote, quote, history));
            assertTrue(error.retryFeedback().contains("新请求"));
        }
    }
}
