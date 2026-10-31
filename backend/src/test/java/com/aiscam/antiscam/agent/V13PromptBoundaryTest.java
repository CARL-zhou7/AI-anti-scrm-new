package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V13PromptBoundaryTest {
    private static String template(String path) throws Exception {
        try (var in = V13PromptBoundaryTest.class.getResourceAsStream("/prompts/" + path + ".txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void newTemplatesRenderAndVictimDoesNotReceiveRoleLabelsOrInternalFields() throws Exception {
        var snapshots = new ConfigurationSnapshots(new ObjectMapper());
        String profile = """
            {"name":"林晓","age":23,"gender":"女","identity":"学生","description":"生活背景","family_status":"独居",
             "selected_scenario_code":"TASK_REBATE","initial_trust_score":50,"positive_factor":2.0,
             "available_funds_yuan":3000,"anti_fraud_awareness":"LOW","victim_scenario_profiles":[{
             "scenario_code":"TASK_REBATE","needs":"补充收入","initial_trust_score":50,"contact_background":"同学介绍"}]}
            """;
        var scene = new AgentScene(ScenarioCode.TASK_REBATE,"刷单返利","简介","背景",snapshots.victimVisibleText(profile),
                "default",template("v4/scammer"),template("v4/victim"),template("v4/narrator"),
                snapshots.scammerVisibleText(profile),true,template("v1/event"),true);
        var received = new DialogueLine(1,SpeakerRole.SCAMMER,"请了解规则",null,"不可披露的内部意图");
        String text = String.join("\n",AgentContextAssembler.victim(scene,List.of(received),received,2,100,false).values());
        for (String hidden : List.of("防骗意识","initial_trust_score","positive_factor","骗子：","受害者：","不可披露的内部意图")) assertFalse(text.contains(hidden),hidden);
        assertFalse(scene.victimPublicProfile().contains("3000"));
        assertFalse(scene.victimPublicProfile().contains("initial_trust_score"));
        var runner = new AgentRunner(new ScriptedLlmClient(new ObjectMapper()), new SimulationProperties().getLlm());
        var funds = new FundsContext(ScenarioCode.TASK_REBATE,3000,9000,0,0,0,List.of());
        assertNotNull(runner.scammer(scene,List.of(),1,1000,0,funds));
        assertNotNull(runner.victim(scene,List.of(),received,1,1000,false,0,funds));
        assertTrue(runner.event(scene,List.of(received),1,1000,0,funds).isEmpty());
    }

    @Test
    void eventsRequireStrictTypesAndKeepVerbatimEvidence() {
        String raw = """
            {"events":[{"type":"NEW_CONTRADICTION","subjectKey":"material:金额","quote":"金额不一样","source":"ACTION"}]}
            """;
        var event = AgentOutputParser.event(raw).getFirst();
        assertEquals("金额不一样",event.quote());
        assertEquals(EvidenceSource.ACTION,event.source());
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(raw.replace("NEW_CONTRADICTION","UNKNOWN")));
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(raw + "{}"));
        assertThrows(AgentCallException.class,()->AgentOutputParser.event(raw.replace("ACTION","SYSTEM")));
    }
}
