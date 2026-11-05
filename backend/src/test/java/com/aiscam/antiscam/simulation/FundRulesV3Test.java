package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class FundRulesV3Test {
    @Test void firstRoundCannotPayLargeOrSplitButLaterRoundCan() {
        var before = new FundsSummary(1000,1000,0,0,0,0,0,0,0);
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(before,context(before,100,List.of()),2,1,
                List.of(action(FundFlowType.PAYMENT,101,1,null))));
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(before,context(before,100,List.of()),2,1,
                List.of(action(FundFlowType.PAYMENT,60,1,null),action(FundFlowType.PAYMENT,60,2,null))));
        var first = FundRules.apply(before,context(before,100,List.of()),2,1,List.of(action(FundFlowType.PAYMENT,100,1,null))).after();
        assertDoesNotThrow(() -> FundRules.apply(first,context(first,100,List.of()),2,2,List.of(action(FundFlowType.PAYMENT,500,1,null))));
    }
    @Test void completedPayoutCannotBypassLedgerWithAnEmptyActionList() {
        var f = new FundsSummary(1000,1000,0,0,0,0,0,0,0);
        var ctx = context(f,50,List.of());
        var error = assertThrows(com.aiscam.antiscam.agent.AgentCallException.class, () ->
                com.aiscam.antiscam.agent.AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                        "500元已发放，注意查收",null,500,List.of(),2,ctx,List.of(),true));
        assertTrue(error.getMessage().contains("matching fund action"));
        assertDoesNotThrow(() -> com.aiscam.antiscam.agent.AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                "完成任务后返500元",null,500,List.of(),2,ctx,List.of(),true));
        assertDoesNotThrow(() -> com.aiscam.antiscam.agent.AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                "已实际转账200元",null,200,List.of(action(FundFlowType.REWARD,200,1,null)),2,ctx,List.of(),true));
        assertThrows(com.aiscam.antiscam.agent.AgentCallException.class, () ->
                com.aiscam.antiscam.agent.AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                        "已实际转账500元",null,500,List.of(action(FundFlowType.REWARD,500,1,null)),2,ctx,List.of(),true));
    }
    private FundsContext context(FundsSummary f, int score, List<FundFlow> flows) {
        return new FundsContext(ScenarioCode.TASK_REBATE,f.availableBalanceYuan(),150,f.reserveReleasedYuan(),
                f.totalPaidYuan(),f.totalRefundedYuan(),flows,f.totalRewardedYuan(),100,50,
                f.generalReserveReleasedYuan(),f.emergencyReserveReleasedYuan(),new ActionGate.TrustGate(score,60,80,90,100));
    }
    private FundAction action(FundFlowType type, int amount, int order, FundReserveTier tier) {
        return new FundAction(type,type==FundFlowType.PAYMENT?FundPurpose.INITIAL_PAYMENT:FundPurpose.valueOf(type.name()),
                amount,2,type==FundFlowType.REWARD?SpeakerRole.SCAMMER:SpeakerRole.VICTIM,
                "已实际转账"+amount+"元",order,type==FundFlowType.RESERVE_RELEASE?"本笔付款不足，动用预留":null,null,tier);
    }
    @Test void rewardBeforePaymentAndSharedPayoutCap() {
        var f=new FundsSummary(1000,1000,0,0,0,0,0,0,0);
        var out=FundRules.apply(f,context(f,50,List.of()),2,1,List.of(action(FundFlowType.REWARD,50,1,null))).after();
        assertEquals(1050,out.availableBalanceYuan()); assertEquals(50,out.totalRewardedYuan()); assertEquals(0,out.actualLossYuan());
        var payment=new FundFlow(1,1,1L,new FundPosting(FundFlowType.PAYMENT,FundPurpose.INITIAL_PAYMENT,100,1,900,null,null),Instant.EPOCH);
        ReflectionTestUtils.setField(payment,"id",1L);
        var paid=new FundsSummary(1000,1190,100,0,290,0,0,0,0);
        var refund=new FundAction(FundFlowType.REFUND,FundPurpose.REFUND,10,2,SpeakerRole.SCAMMER,"已退10元",1,null,1L,null);
        var refunded=FundRules.apply(paid,context(paid,80,List.of(payment)),2,2,List.of(refund)).after();
        assertEquals(300,refunded.totalRewardedYuan()+refunded.totalRefundedYuan());
        assertThrows(IllegalArgumentException.class,()->FundRules.apply(paid,context(paid,80,List.of(payment)),2,2,List.of(action(FundFlowType.REWARD,11,1,null))));
        var loan=new FundsContext(ScenarioCode.IMPERSONATION_LOAN,1000,150,0,0,0,List.of(),0,100,50,0,0,new ActionGate.TrustGate(100,60,80,90,100));
        assertThrows(IllegalArgumentException.class,()->FundRules.apply(f,loan,2,1,List.of(action(FundFlowType.REWARD,1,1,null))));
    }
    @Test void reservesOnlyCoverSameMessageGapAndUseGeneralFirst() {
        var f=new FundsSummary(110,10,100,0,0,0,0,0,0); var ctx=context(f,90,List.of());
        var general=action(FundFlowType.RESERVE_RELEASE,100,1,FundReserveTier.GENERAL);
        var emergency=action(FundFlowType.RESERVE_RELEASE,40,2,FundReserveTier.EMERGENCY);
        var payment=action(FundFlowType.PAYMENT,150,3,null);
        var out=FundRules.apply(f,ctx,2,1,List.of(general,emergency,payment));
        assertEquals(0,out.after().availableBalanceYuan()); assertEquals(100,out.after().generalReserveReleasedYuan());
        assertEquals(40,out.after().emergencyReserveReleasedYuan()); assertEquals(FundReserveTier.EMERGENCY,out.postings().get(1).reserveTier());
        assertThrows(IllegalArgumentException.class,()->FundRules.apply(f,ctx,2,1,List.of(general)));
        assertThrows(IllegalArgumentException.class,()->FundRules.apply(f,ctx,2,1,List.of(action(FundFlowType.RESERVE_RELEASE,40,1,FundReserveTier.EMERGENCY),payment)));
        assertThrows(IllegalArgumentException.class,()->FundRules.apply(f,ctx,2,1,List.of(general,emergency,action(FundFlowType.PAYMENT,100,3,null))));
    }
    @Test void thresholdAndCumulativePrincipalCannotBeBypassedWithRewardsOrSplitPayments() {
        var f=new FundsSummary(1000,1000,0,0,0,0,0,0,0);
        assertThrows(IllegalArgumentException.class,()->FundRules.apply(f,context(f,79,List.of()),2,1,List.of(action(FundFlowType.PAYMENT,50,1,null))));
        assertDoesNotThrow(()->FundRules.apply(f,context(f,80,List.of()),2,1,List.of(action(FundFlowType.PAYMENT,100,1,null))));
        assertThrows(IllegalArgumentException.class,()->FundRules.apply(f,context(f,89,List.of()),2,1,List.of(action(FundFlowType.PAYMENT,60,1,null),action(FundFlowType.PAYMENT,50,2,null))));
        var rewarded=new FundsSummary(1000,1100,100,0,200,0,0,0,0);
        assertThrows(IllegalArgumentException.class,()->FundRules.apply(rewarded,context(rewarded,80,List.of()),2,1,List.of(action(FundFlowType.PAYMENT,1,1,null))));
        var emergency=action(FundFlowType.RESERVE_RELEASE,1,1,FundReserveTier.EMERGENCY);
        assertEquals(ActionGate.GateDecision.NEED_LARGE,ActionGate.decide(new ActionGate.TrustGate(89,60,80,90,100),emergency,0,0,0,1));
    }
}
