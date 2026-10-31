package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.WaitingItemService;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventMaterialHandlingTest {
    @Test void quoteFailureRetainsRejectedOutputAndCorrectsAgainstTheCurrentSourceWithinRetryBudget() throws Exception {
        var props=new SimulationProperties();props.getLlm().setTransientRetries(1);
        var scene=new AgentScene(ScenarioCode.FAKE_INVESTMENT,"投资","简介","联系人","本人","test",
                "unused","unused","unused","公开背景",true,template("v17/event"),true,true);
        String action="发送《智科观盘通道开通指引》PDF（含二维码图标、扫码即开户字样、无真实可扫描内容）";
        String quote="《智科观盘通道开通指引》PDF";
        var mapper=new ObjectMapper();
        var output=mapper.createObjectNode();
        var event=output.putArray("events").addObject();
        event.put("type","RELEVANT_MATERIAL").put("subjectKey","material:观盘通道").put("progressKey","sent:观盘指引")
                .put("source","CONTENT").put("quote",quote);
        String rejected=output.toString();event.put("source","ACTION");String corrected=output.toString();
        var prompts=new ArrayList<String>();
        var runner=new AgentRunner(request->{prompts.add(request.prompt());return prompts.size()==1?rejected:corrected;},props.getLlm());
        var contact=new DialogueLine(6,SpeakerRole.SCAMMER,"现在注册就能看到实时持仓和盈亏。",action,"PRIVATE_INTENT");
        var live=new SimulationOrchestrator.Live(58);
        var result=orchestrator(props).retry(live,feedback->runner.event(scene,List.of(contact),6,10000,
                live.nextAttempt(AgentRole.EVENT,6),null,"",feedback));
        assertEquals(2,prompts.size());assertEquals(1,result.size());
        assertEquals(EvidenceSource.ACTION,result.getFirst().source());assertEquals(quote,result.getFirst().quote());
        assertTrue(prompts.getLast().contains(rejected));
        assertTrue(prompts.getLast().contains("CONTENT："+contact.content()));
        assertTrue(prompts.getLast().contains("ACTION："+action));
        assertFalse(prompts.getLast().substring(prompts.getLast().indexOf("【本轮EVENT可引用原文】")).contains("PRIVATE_INTENT"));
    }

    private static final String REQUEST="把事故处理通知发来看看";
    private static final String QUOTE="发送事故处理通知，事故日期为昨天下午，金额12000元，收款户名为某处理方，用途事故垫款";
    private String raw() throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("events",List.of(Map.of(
                "type","NEW_EFFECTIVE_EXPLANATION","subjectKey","material:事故通知","quote",QUOTE,"source","ACTION",
                "materials",List.of(Map.of("subjectKey","material:事故通知","amountYuan",12000,"date","昨天下午","accountName","某处理方","purpose","事故垫款")),
                "requirement",Map.of("type","MATERIAL","subjectKey","material:事故通知","text","提供通知","basis",REQUEST)))));
    }
    private String template(String name) throws Exception {
        try(var in=getClass().getResourceAsStream("/prompts/"+name+".txt")) {
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private SimulationOrchestrator orchestrator(SimulationProperties props) {
        var sessions=mock(SimulationSessionRepository.class);var session=mock(SimulationSession.class);
        when(session.isRunning()).thenReturn(true);when(session.getDeadlineAt()).thenReturn(Instant.now().plusSeconds(120));
        when(sessions.findById(58L)).thenReturn(Optional.of(session));
        return new SimulationOrchestrator(null,sessions,null,null,null,null,null,null,null,null,props,null,null,Clock.systemUTC(),null,null);
    }

    @Test void incompleteMaterialKeepsTheEventAndLeavesTheRequestedItemOpen() throws Exception {
        var event=AgentOutputParser.event(raw()).getFirst();
        assertEquals(QUOTE,event.quote());assertNotNull(event.requirement());assertTrue(event.materials().isEmpty());
        var items=mock(WaitingItemRepository.class);var sessions=mock(SimulationSessionRepository.class);
        var rounds=mock(SimulationRoundRepository.class);var messages=mock(SimulationMessageRepository.class);
        when(items.findBySessionIdAndItemTypeAndSubjectKey(anyLong(),any(),anyString())).thenReturn(Optional.empty());
        when(items.save(any())).thenAnswer(c -> c.getArgument(0));
        var session=new SimulationSession(1,"test",1,1,1,"{}","{}","{}",1,2,3,4,
                "{\"waiting_ruleset_version\":\"waiting-v1\"}",Instant.EPOCH,Instant.EPOCH,"{}",1000);
        org.springframework.test.util.ReflectionTestUtils.setField(session,"id",58L);
        when(rounds.findBySessionIdOrderByRoundNoAsc(58L)).thenReturn(List.of(new SimulationRound(58,3,Instant.EPOCH)));
        when(messages.findByRoundIdIn(anyList())).thenReturn(List.of(new SimulationMessage(3,SpeakerRole.VICTIM,REQUEST,null,null,Instant.EPOCH)));
        var waiting=new WaitingItemService(items,sessions,rounds,messages,new ConfigurationSnapshots(new ObjectMapper()));
        waiting.recordMaterials(session,4,new SimulationMessage(4,SpeakerRole.SCAMMER,"通知发来了",QUOTE,null,Instant.EPOCH),List.of(event));
        var saved=org.mockito.ArgumentCaptor.forClass(WaitingItem.class);
        verify(items).save(saved.capture());assertEquals(WaitingItemStatus.OPEN,saved.getValue().getStatus());
        assertNull(saved.getValue().getResolutionText());
    }

    @Test void completeMaterialStillRequiresVerbatimEvidenceAndStrictStructure() throws Exception {
        String complete=raw().replace("昨天下午","10月25日").replace("某处理方","刘建国");
        assertEquals(1,AgentOutputParser.event(complete).getFirst().materials().size());
        assertTrue(AgentOutputParser.event(raw().replace("昨天下午","10月25日")).getFirst().materials().isEmpty());
        var forged=new ObjectMapper().readTree(complete);
        ((com.fasterxml.jackson.databind.node.ObjectNode)forged.path("events").get(0).path("materials").get(0)).put("date","10月26日");
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(forged.toString()));
        for(String amount:List.of("-1","12000.5","\"12000\""))
            assertThrows(AgentCallException.class,()->AgentOutputParser.event(complete.replace("\"amountYuan\":12000","\"amountYuan\":"+amount)));
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(complete+"{}"));
        assertNotNull(new db.migration.V27__allow_incomplete_material_dialogue().getChecksum());
    }

    @ParameterizedTest
    @EnumSource(value=AgentRole.class,names={"EVENT","VICTIM"})
    void remainingRolesAlsoReceiveTheLastFailureWithinTheSameBudget(AgentRole role) throws Exception {
        var props=new SimulationProperties();props.getLlm().setTransientRetries(1);
        var scene=new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"借款","简介","联系人","本人","test",
                template("v9/scammer"),template("v7/victim"),template("v7/narrator"),"公开背景",true,template("v4/event"),true);
        var prompts=new ArrayList<String>();String validEvent=raw();
        var runner=new AgentRunner(request -> {
            prompts.add(request.prompt());
            if(prompts.size()==1)return "{";
            return role==AgentRole.EVENT ? validEvent : "{\"line\":\"通知我收到了，里面的信息还不完整\",\"action\":null,\"psychology\":\"担心情况\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"fundActions\":[]}";
        },props.getLlm());
        var funds=new FundsContext(ScenarioCode.IMPERSONATION_LOAN,3000,9000,0,0,0,List.of());
        var contact=new DialogueLine(4,SpeakerRole.SCAMMER,"处理通知发来了",QUOTE,null);
        var live=new SimulationOrchestrator.Live(58);
        var engine=orchestrator(props);
        if(role==AgentRole.EVENT) {
            var result=engine.retry(live,feedback -> runner.event(scene,List.of(contact),4,10000,
                    live.nextAttempt(role,4),funds,"",feedback));
            assertEquals(1,result.size());assertTrue(result.getFirst().materials().isEmpty());
        } else engine.retry(live,feedback -> runner.victim(scene,List.of(),contact,4,10000,false,
                live.nextAttempt(role,4),funds,"",feedback));
        assertEquals(2,prompts.size());
        assertFalse(prompts.getFirst().contains("上次输出未通过校验"));
        assertTrue(prompts.get(1).contains("校验原因："));
        assertTrue(prompts.get(1).contains("JSON"));
    }
    @Test void enumFailuresIdentifyTheFieldAndAllowedValuesWithoutAcceptingUnknownTypes() throws Exception {
        for(String field:List.of("type","source","requirement")) {
            var json=new ObjectMapper().readTree(raw());
            var item=(com.fasterxml.jackson.databind.node.ObjectNode)json.path("events").get(0);
            String expected;
            if(field.equals("type")) {item.put("type","MATERIAL_PROVIDED");expected="TrustEventType field type";}
            else if(field.equals("source")) {item.put("source","TEXT");expected="EvidenceSource field source";}
            else {((com.fasterxml.jackson.databind.node.ObjectNode)item.path("requirement")).put("type","CONSULTATION");expected="WaitingItemType field type";}
            var failure=assertThrows(AgentCallException.class,()->AgentOutputParser.event(json.toString()));
            assertTrue(failure.getMessage().contains(expected));
            assertTrue(failure.getMessage().contains("allowed values:"));
            assertFalse(failure.getMessage().contains("MATERIAL_PROVIDED"));
        }
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(raw().replace("NEW_EFFECTIVE_EXPLANATION","INDEPENDENT_SUPPORT")));
    }

    @Test void invalidTrustEventGetsAnActionableRetryAndMayCorrectToNoNewEvent() throws Exception {
        var props=new SimulationProperties();props.getLlm().setTransientRetries(1);
        var scene=new AgentScene(ScenarioCode.FAKE_INVESTMENT,"投资","简介","联系人","本人","test",
                "unused","unused","unused","公开背景",true,template("v5/event"),true);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        String invalid=raw().replace("NEW_EFFECTIVE_EXPLANATION","MATERIAL_PROVIDED");
        var runner=new AgentRunner(request -> {
            if(calls.incrementAndGet()==1)return invalid;
            assertTrue(request.prompt().contains("invalid TrustEventType field type; allowed values:"));
            assertTrue(request.prompt().contains("REPEAT_NO_PROGRESS"));
            return "{\"events\":[]}";
        },props.getLlm());
        var live=new SimulationOrchestrator.Live(58);
        var result=orchestrator(props).retry(live,feedback -> runner.event(scene,List.of(),5,10000,
                live.nextAttempt(AgentRole.EVENT,5),new FundsContext(ScenarioCode.FAKE_INVESTMENT,3000,9000,0,0,0,List.of()),"",feedback));
        assertEquals(2,calls.get());assertTrue(result.isEmpty());
    }

}
