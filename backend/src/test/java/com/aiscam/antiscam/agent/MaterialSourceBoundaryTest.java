package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.service.WaitingItemService;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialSourceBoundaryTest {
    private static final String REQUEST = "把缴费单拍给我看，或让家属说句话";
    private static final String MATERIAL = "聊天中发送一张缴费单照片，显示金额15000元，日期10月25日，户名刘建国，用途急诊缴费";
    private String template(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/prompts/" + name + ".txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private TrustEventCandidate event() {
        return new TrustEventCandidate(TrustEventType.NEW_EFFECTIVE_EXPLANATION,"material:缴费单",MATERIAL,
                EvidenceSource.ACTION,List.of(new Material("material:缴费单",15000,"10月25日","刘建国","急诊缴费")),
                new Requirement(WaitingItemType.MATERIAL,"material:缴费单","提供缴费单",REQUEST));
    }

    @Test void allFourRolePromptsRenderWithoutLeakingPrivateForgeryDetailsToVictim() throws Exception {
        var mapper = new ObjectMapper();
        var scene = new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"亲友借款","简介","角色背景","本人背景","test",
                template("v8/scammer"),template("v6/victim"),template("v6/narrator"),"公开背景",true,template("v3/event"),true);
        var history = List.of(new DialogueLine(6,SpeakerRole.VICTIM,REQUEST,null,null));
        var sent = new DialogueLine(7,SpeakerRole.SCAMMER,"单子发来了，你看看",MATERIAL,"PRIVATE-FORGERY-DETAIL");
        var funds = new FundsContext(ScenarioCode.IMPERSONATION_LOAN,3000,9000,0,0,0,List.of());
        var prompts = new EnumMap<AgentRole,String>(AgentRole.class);
        var runner = new AgentRunner(request -> {
            prompts.put(request.role(),request.prompt());
            try {
                return switch(request.role()) {
                    case SCAMMER -> mapper.writeValueAsString(new ScammerTurn(sent.content(),sent.action(),sent.internalNote(),new ToneHint(Speed.NORMAL,"自然"),null,List.of()));
                    case VICTIM -> mapper.writeValueAsString(new VictimTurn("单子我看到了，身份还得确认",null,"仍有疑问",new ToneHint(Speed.NORMAL,"自然"),List.of()));
                    case EVENT -> mapper.writeValueAsString(Map.of("events",List.of(event())));
                    case NARRATOR -> mapper.writeValueAsString(new NarratorJudgement("提供佐证","展示材料","来源受控","保持谨慎",25,"尚未独立核实",false,false,false,List.of(),null,null,false,null,List.of()));
                    default -> throw new IllegalStateException();
                };
            } catch (Exception e) { throw new IllegalStateException(e); }
        },new SimulationProperties().getLlm());
        runner.scammer(scene,history,7,10000,0,funds);
        runner.victim(scene,history,sent,7,10000,false,0,funds);
        assertEquals(1,runner.event(scene,List.of(history.getFirst(),sent),7,10000,0,funds).getFirst().materials().size());
        var lines = List.of(history.getFirst(),sent,new DialogueLine(7,SpeakerRole.VICTIM,"单子我看到了，身份还得确认",null,"内部"));
        assertTrue(runner.narrator(scene,lines,7,PriorFacts.none(),10000,0,funds,null,false,List.of()).mergedEvidence().isEmpty());
        assertEquals(4,prompts.size());
        assertTrue(prompts.get(AgentRole.SCAMMER).contains("不需要真实上传图片或接入第三个人"));
        assertTrue(prompts.get(AgentRole.VICTIM).contains(MATERIAL));
        assertFalse(prompts.get(AgentRole.VICTIM).contains("PRIVATE-FORGERY-DETAIL"));
        assertTrue(prompts.get(AgentRole.VICTIM).contains("不能仅凭图片、声音或对方自称判定对方真实"));
        assertTrue(prompts.get(AgentRole.NARRATOR).contains("对方控制渠道的信息"));
    }

    @Test void textualMaterialCanSatisfyARequestWithoutCreatingIndependentVerification() {
        var items=mock(WaitingItemRepository.class); var sessions=mock(SimulationSessionRepository.class);
        var rounds=mock(SimulationRoundRepository.class); var messages=mock(SimulationMessageRepository.class);
        var saved=new ArrayList<WaitingItem>();
        when(items.findBySessionIdOrderByIdAsc(1L)).thenReturn(saved);
        when(items.findBySessionIdAndItemTypeAndSubjectKey(anyLong(),any(),anyString())).thenAnswer(call -> saved.stream()
                .filter(i -> i.getItemType()==call.getArgument(1) && i.getSubjectKey().equals(call.getArgument(2))).findFirst());
        when(items.save(any())).thenAnswer(call -> {var item=(WaitingItem)call.getArgument(0);saved.add(item);return item;});
        var now=Instant.EPOCH;
        var session=new SimulationSession(1,"request",1,1,1,"{}","{}","{}",1,2,3,4,
                "{\"waiting_ruleset_version\":\"waiting-v1\"}",now,now,"{}",1000);
        org.springframework.test.util.ReflectionTestUtils.setField(session,"id",1L);
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        var request=new SimulationMessage(6,SpeakerRole.VICTIM,REQUEST,null,null,now);
        when(rounds.findBySessionIdOrderByRoundNoAsc(1L)).thenReturn(List.of(new SimulationRound(1,6,now)));
        when(messages.findByRoundIdIn(anyList())).thenReturn(List.of(request));
        var service=new WaitingItemService(items,sessions,rounds,messages,new ConfigurationSnapshots(new ObjectMapper()));
        var supplied=new SimulationMessage(7,SpeakerRole.SCAMMER,"单子发来了",MATERIAL,"PRIVATE-FORGERY-DETAIL",now);
        AgentOutputValidator.trustEvent(event(),false);
        service.recordMaterials(session,7,supplied,List.of(event()));
        service.recordMaterials(session,7,supplied,List.of(event()));
        assertEquals(1,saved.size());
        assertEquals(WaitingItemType.MATERIAL,saved.getFirst().getItemType());
        assertEquals(WaitingItemStatus.SATISFIED,saved.getFirst().getStatus());
        assertTrue(service.facts(1,false,false).contains("齐全不表示真实"));
        assertFalse(service.facts(1,false,false).contains("PRIVATE-FORGERY-DETAIL"));
    }

    @Test void newMigrationRegistersAllFivePromptsAndKeepsReportSourceBoundary() throws Exception {
        var connection=mock(java.sql.Connection.class);
        var disable=mock(java.sql.PreparedStatement.class);var insert=mock(java.sql.PreparedStatement.class);
        when(connection.prepareStatement(startsWith("UPDATE"))).thenReturn(disable);
        when(connection.prepareStatement(startsWith("INSERT"))).thenReturn(insert);
        var context=mock(org.flywaydb.core.api.migration.Context.class);
        when(context.getConnection()).thenReturn(connection);
        var migration=new db.migration.V25__clarify_material_and_verification_sources();
        migration.migrate(context);
        assertNotNull(migration.getChecksum());
        verify(disable,times(5)).executeUpdate();verify(insert,times(5)).executeUpdate();
        verify(insert).setInt(2,7);verify(insert).setInt(2,5);verify(insert).setInt(2,6);
        verify(insert).setInt(2,3);verify(insert).setInt(2,4);
        assertTrue(template("v6/report").contains("不将长期拉扯美化成多次有效核实"));
    }
}
