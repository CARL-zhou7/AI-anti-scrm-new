package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.agent.LlmClient.LlmRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptedV13CompatibilityTest {
    private final ScriptedLlmClient client=new ScriptedLlmClient(new ObjectMapper());
    private String call(AgentRole role,int round,String script,String prompt) {
        return client.completeJson(new LlmRequest(role,prompt,0,4096,Duration.ofSeconds(2),round,0,"剧本:"+script));
    }
    @Test void eventMaterialCounterEvidenceAndFailureAreScriptable() {
        var materials=AgentOutputParser.event(call(AgentRole.EVENT,1,"trust-v1",""));
        assertEquals(1,materials.getFirst().materials().size());
        var victim=AgentOutputParser.victim(call(AgentRole.VICTIM,1,"trust-v1",""));
        assertTrue(victim.line().contains("什么时候截止"));
        assertTrue(AgentOutputParser.narrator(call(AgentRole.NARRATOR,3,"trust-v1",""))
                .evidence().stream().anyMatch(e -> e.factCode()==com.aiscam.antiscam.simulation.rules.FactCode.DECISIVE_COUNTEREVIDENCE_FOUND));
        var error=assertThrows(AgentCallException.class,()->call(AgentRole.EVENT,1,"trust-event-fail",""));
        assertEquals(AgentCallException.Category.ANALYSIS,error.category());
    }
    @Test void reportV1V2V3FollowTheFrozenRequestAndKeepNoOutcomeReason() throws Exception {
        String funds="\ntotalPaidYuan=100\ntotalRefundedYuan=10\ntotalRewardedYuan=50\nactualLossYuan=40\navailableBalanceYuan=960\ngeneralReserveRemainingYuan=1000\nemergencyReserveRemainingYuan=500";
        assertNotNull(AgentOutputParser.report(call(AgentRole.REPORT,5,"default","roundAnalyses")));
        assertEquals(2,AgentOutputParser.reportV2(call(AgentRole.REPORT,5,"report-v3","\"contentSchemaVersion\":2\n已受骗：已付款"+funds)).contentSchemaVersion());
        var v3=AgentOutputParser.reportV3(call(AgentRole.REPORT,2,"trust-v1","\"contentSchemaVersion\":3\n未形成受骗或识破结论：用户离开"+funds));
        assertTrue(v3.result().reason().contains("用户离开")); assertEquals(50,v3.fundsSummary().totalRewardedYuan());
        assertEquals(960,v3.fundsSummary().availableBalanceYuan());
    }
}
