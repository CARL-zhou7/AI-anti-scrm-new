package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.FundsContext;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NonfinancialMaterialTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private static final String QUOTE="发送设备材料企业订单与政策节点核对表，可见企业简称A、2024年三季度订单同比增幅区间、政策补贴申报截止月份";
    private ObjectNode event() {
        var root=mapper.createObjectNode();
        var event=root.putArray("events").addObject();
        event.put("type","NEW_EFFECTIVE_EXPLANATION").put("subjectKey","material:订单政策表")
                .put("quote",QUOTE).put("source","ACTION");
        event.putNull("requirement");event.putArray("materials");
        return root;
    }
    private ObjectNode addMaterial(ObjectNode root) {
        return ((ObjectNode)root.path("events").get(0)).withArray("materials").addObject().put("subjectKey","material:订单政策表");
    }
    private String template(String name) throws Exception {
        try(var in=getClass().getResourceAsStream("/prompts/"+name+".txt")) {
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    @Test void ordinaryResearchDocumentWithAbsentNullBlankOrZeroMetadataDoesNotFailTheEvent() throws Exception {
        for(int variant=0;variant<4;variant++) {
            var raw=event();var material=addMaterial(raw);
            if(variant==1) material.putNull("amountYuan").putNull("date").putNull("accountName").putNull("purpose");
            if(variant==2) material.putNull("amountYuan").put("date","").put("accountName","").put("purpose","");
            if(variant==3) material.put("amountYuan",0).put("date","2024年三季度").putNull("accountName").put("purpose","行业研究");
            var result=AgentOutputParser.event(raw.toString());
            assertEquals(1,result.size());assertEquals(QUOTE,result.getFirst().quote());
            assertTrue(result.getFirst().materials().isEmpty());
            AgentOutputValidator.trustEvent(result.getFirst(),false);
        }
        var scene=new AgentScene(ScenarioCode.FAKE_INVESTMENT,"投资","简介","联系人","本人","test",
                "unused","unused","unused","公开背景",true,template("v5/event"),true);
        var raw=event();addMaterial(raw);
        var runner=new AgentRunner(request -> raw.toString(),new SimulationProperties().getLlm());
        var result=runner.event(scene,List.of(new DialogueLine(5,SpeakerRole.SCAMMER,"发您一份核对",QUOTE,null)),
                5,10000,0,new FundsContext(ScenarioCode.FAKE_INVESTMENT,3000,9000,0,0,0,List.of()));
        assertEquals(1,result.size());assertTrue(result.getFirst().materials().isEmpty());
        assertNotNull(new db.migration.V28__accept_nonfinancial_material_context().getChecksum());
    }

    @Test void optionalMetadataDoesNotAdmitWrongTypesUnknownFieldsOrExcessiveText() {
        for(String field:List.of("date","accountName","purpose")) {
            var raw=event();addMaterial(raw).put(field,42);
            assertThrows(AgentCallException.class,()->AgentOutputParser.event(raw.toString()));
            var tooLong=event();addMaterial(tooLong).put(field,"字".repeat(65));
            assertThrows(AgentCallException.class,()->AgentOutputParser.event(tooLong.toString()));
        }
        for(String amount:List.of("-1","1.5","\"12000\"","true")) {
            var raw=event();addMaterial(raw).set("amountYuan",read(amount));
            assertThrows(AgentCallException.class,()->AgentOutputParser.event(raw.toString()));
        }
        var unknown=event();addMaterial(unknown).put("unrecognized","value");
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(unknown.toString()));
        var noSubject=event();addMaterial(noSubject).remove("subjectKey");
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(noSubject.toString()));
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(event()+"{}"));
    }
    private com.fasterxml.jackson.databind.JsonNode read(String raw) {
        try{return mapper.readTree(raw);}catch(Exception e){throw new IllegalStateException(e);}
    }
}
