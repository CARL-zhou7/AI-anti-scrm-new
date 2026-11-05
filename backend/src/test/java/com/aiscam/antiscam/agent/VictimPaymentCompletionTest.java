package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class VictimPaymentCompletionTest {
    private void validate(String line, String action, String quote) {
        var funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT, 3000, 0, 0, 0, 0, List.of());
        var payment = new FundAction(FundFlowType.PAYMENT, FundPurpose.OTHER_PAYMENT,
                300, 13, SpeakerRole.VICTIM, quote, 1, null, null);
        AgentOutputValidator.funds(SpeakerRole.VICTIM, line, action, null, List.of(payment), 13, funds, List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"通过微信扫码支付300元解冻金", "向平台账户转账300元解冻金", "已缴纳300元解冻金", "缴纳了300元解冻金", "已交纳300元解冻金"})
    void acceptsPaymentNarrationInAction(String action) {
        assertDoesNotThrow(() -> validate("操作完成了。", action, action));
    }

    @ParameterizedTest
    @ValueSource(strings = {"准备扫码支付300元解冻金", "打算转账300元", "尚未支付300元", "需要扫码支付300元", "待支付300元", "想支付300元", "请支付300元", "扫码支付300元后才能提现", "已同意缴纳300元", "打开支付页面并输入300元", "扫码查看300元解冻金", "点击支付300元按钮", "尝试支付300元", "支付300元失败", "正在支付300元", "等待支付300元确认", "询问是否支付300元"})
    void rejectsIntentionAndPreparation(String action) {
        assertThrows(AgentCallException.class, () -> validate("正在了解。", action, action));
    }

    @ParameterizedTest
    @ValueSource(strings = {"准备扫码支付300元解冻金", "需要扫码支付300元", "扫码支付300元后才能提现"})
    void rejectsQuoteThatOmitsCondition(String action) {
        assertThrows(AgentCallException.class, () -> validate("正在了解。", action, "扫码支付300元"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"扫码支付300元解冻金", "转账300元解冻金"})
    void dialogueStillNeedsExplicitCompletion(String line) {
        assertThrows(AgentCallException.class, () -> validate(line, null, line));
    }
}
