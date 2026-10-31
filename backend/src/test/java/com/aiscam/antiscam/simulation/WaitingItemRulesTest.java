package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.*;
import com.aiscam.antiscam.simulation.service.WaitingItemService;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaitingItemRulesTest {
    @Test void terminalStatesDoNotReopenAndAbandonDoesNotVerify() {
        var item=new WaitingItem(1,WaitingItemType.MATERIAL,"material:任务","补齐材料","缺少日期",1);
        item.resolve(WaitingItemStatus.SATISFIED,"金额100元，日期10月25日，户名甲，用途任务",2);
        item.refreshRequest("重新补齐","仍然缺少日期");
        assertEquals("补齐材料",item.getRequestText());
        assertThrows(IllegalStateException.class,()->item.resolve(WaitingItemStatus.OPEN,null,3));
        var answered=new WaitingItem(1,WaitingItemType.CONSULTATION,"consult:third-party","咨询","已发消息咨询",1);
        answered.resolve(WaitingItemStatus.RESPONDED,"先别付钱",2);
        answered.resolve(WaitingItemStatus.ABANDONED,"我不再等了",3);
        assertEquals("先别付钱",answered.getResolutionText());
        var pending=new WaitingItem(1,WaitingItemType.VERIFICATION,"verify:身份","核实身份","正在拨打电话核实",1);
        pending.resolve(WaitingItemStatus.ABANDONED,"我不再等待核实",2);
        assertEquals(WaitingItemStatus.ABANDONED,pending.getStatus());
        assertThrows(IllegalStateException.class,()->pending.resolve(WaitingItemStatus.SATISFIED,"假定通过",3));
    }
    @Test void strictDetailsRejectVagueMaterialsAndUnsentConsultations() {
        String raw="""
            {"events":[{"type":"NEW_EFFECTIVE_EXPLANATION","subjectKey":"material:任务","quote":"金额100元，日期10月25日，户名任务方，用途体验任务",
            "source":"ACTION","materials":[{"subjectKey":"material:任务","amountYuan":100,"date":"10月25日","accountName":"任务方","purpose":"体验任务"}],"requirement":null}]}
            """;
        assertEquals(100,AgentOutputParser.event(raw).getFirst().materials().getFirst().amountYuan());
        assertTrue(AgentOutputParser.event(raw.replace("10月25日","不详")).getFirst().materials().isEmpty());
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(raw.replace("\"amountYuan\":100","\"amountYuan\":100.5")));
        assertTrue(AgentOutputParser.event("{\"events\":[]}").isEmpty());
        var update=new WaitingUpdate(WaitingAction.OPEN,WaitingItemType.CONSULTATION,"consult:third-party","问同学","准备向同学咨询");
        assertThrows(AgentCallException.class,()->AgentOutputValidator.waitingUpdate(update));
        assertFalse(WaitingItemRules.executed("暂时联系不上，不再等待",true));
    }
    @Test void consultationIsSavedOnceAndReplyArrivesBeforeNextTurnWithoutPrivateLeak() {
        var items=mock(WaitingItemRepository.class); var sessions=mock(SimulationSessionRepository.class);
        var rounds=mock(SimulationRoundRepository.class); var messages=mock(SimulationMessageRepository.class);
        var saved=new ArrayList<WaitingItem>();
        when(items.findBySessionIdOrderByIdAsc(1)).thenReturn(saved);
        when(items.findBySessionIdAndItemTypeAndSubjectKey(anyLong(),any(),anyString())).thenAnswer(call -> saved.stream()
                .filter(i -> i.getItemType()==call.getArgument(1) && i.getSubjectKey().equals(call.getArgument(2))).findFirst());
        when(items.save(any())).thenAnswer(call -> { var i=(WaitingItem)call.getArgument(0); saved.add(i); return i; });
        var now=Instant.EPOCH;
        var session=new SimulationSession(1,"request",1,1,1,"{\"scenario_code\":\"IMPERSONATION_LOAN\"}","{}","{}",1,2,3,4,
                "{\"waiting_ruleset_version\":\"waiting-v1\"}",now,now,"{}",1000);
        ReflectionTestUtils.setField(session,"id",1L); session.enterRound(1);
        when(sessions.lockById(1)).thenReturn(Optional.of(session)); when(sessions.findById(1L)).thenReturn(Optional.of(session));
        var service=new WaitingItemService(items,sessions,rounds,messages,new ConfigurationSnapshots(new ObjectMapper()));
        var message=new SimulationMessage(1,SpeakerRole.VICTIM,"我已拨打原有电话联系本人",null,"内部",now);
        var update=new WaitingUpdate(WaitingAction.OPEN,WaitingItemType.CONSULTATION,"consult:claimed-person","联系本人","已拨打原有电话联系本人");
        service.recordVictim(session,1,message,List.of(update)); service.recordVictim(session,1,message,List.of(update));
        assertEquals(1,saved.size()); service.beforeVictim(1,1); assertEquals(WaitingItemStatus.OPEN,saved.getFirst().getStatus());
        session.enterRound(2); service.beforeVictim(1,2);
        assertEquals(WaitingItemStatus.RESPONDED,saved.getFirst().getStatus()); assertEquals(2,saved.getFirst().getResolvedRoundNo());
        String facts=service.facts(1,false,false); assertTrue(facts.contains("没跟你借过钱"));
        assertFalse(facts.contains("RESPONDED")); assertFalse(facts.contains("consult:claimed-person")); assertEquals("",service.facts(1,true,false));
        var legacy=new SimulationSession(1,"old",1,1,1,"{}","{}","{}",1,2,3,4,"{}",now,now,"{}",1000);
        when(sessions.findById(2L)).thenReturn(Optional.of(legacy)); assertEquals("",service.facts(2,false,false));
    }
    @Test void feedbackDependsOnScenarioAndTargetOnly() {
        var denial=FriendFeedbackRules.reply(ScenarioCode.IMPERSONATION_LOAN,FriendFeedbackRules.ConsultTarget.CLAIMED_PERSON);
        assertTrue(denial.decisive()); assertTrue(denial.text().contains("没跟你借过钱"));
        for (var scenario : ScenarioCode.values()) assertFalse(FriendFeedbackRules.reply(scenario,FriendFeedbackRules.ConsultTarget.THIRD_PARTY).decisive());
        assertTrue(FriendFeedbackRules.reply(ScenarioCode.IMPERSONATION_LOAN,FriendFeedbackRules.ConsultTarget.THIRD_PARTY).text().contains("联系不上"));
    }
}
