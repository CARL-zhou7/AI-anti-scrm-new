package com.aiscam.antiscam.report;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.report.engine.ReportExecutor;
import com.aiscam.antiscam.report.service.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportGenerationBudgetTest {
    private static ReportContentV2 content() {
        return new ReportContentV2(2,new ReportResult("已识破","核实后停止联系"),"文".repeat(400),List.of(),
                List.of("通过原有号码核实","付款前独立确认"),new ReportFundsSummary(0,0,0));
    }
    private static ReportContext context() {
        return new ReportContext("报告 json",Map.of(),"default",19,"已识破",2,new ReportFundsSummary(0,0,0),Map.of(),2);
    }
    @Test void reportsHaveSpaceForFullJsonAndUseTheirOwnAttemptDeadline() throws Exception {
        var requests=new ArrayList<LlmClient.LlmRequest>();
        var json=new ObjectMapper();var config=new SimulationProperties.Llm();
        var runner=new AgentRunner(request->{
            requests.add(request);
            try {
                return request.role()==AgentRole.REPORT?json.writeValueAsString(content()):
                        "{\"line\":\"你好\",\"action\":null,\"intent\":\"建立联系\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"}}";
            } catch(Exception e){throw new AssertionError(e);}
        },config);
        assertEquals(content(),runner.report(context(),120000));
        assertTrue(requests.getFirst().maxTokens()>=4096);assertEquals(Duration.ofSeconds(120),requests.getFirst().timeout());
        String template;
        try(var in=getClass().getResourceAsStream("/prompts/v1/scammer.txt")) {
            template=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var scene=new AgentScene(ScenarioCode.TASK_REBATE,"场景","简介","骗子","受害者","default",template,"你好","你好","公开经历",false);
        runner.scammer(scene,List.of(),1,120000,0,null);
        assertEquals(800,requests.get(1).maxTokens());assertEquals(Duration.ofSeconds(20),requests.get(1).timeout());
    }
    @Test void executorPassesRemainingReportBudgetInsteadOfDialogueTimeout() throws Exception {
        Instant now=Instant.parse("2026-10-23T00:00:00Z");
        var reports=mock(SimulationReportRepository.class);var transitions=mock(ReportTransitions.class);
        var contexts=mock(ReportContextLoader.class);var runner=mock(AgentRunner.class);
        var claimed=new ReportTransitions.Claimed(35,2,"test-token",now,now.plusSeconds(120));
        var context=context();var done=new CountDownLatch(1);
        when(reports.findTop5ByStatusOrderByQueuedAtAscSessionIdAsc(ReportStatus.PENDING)).thenReturn(List.of(SimulationReport.pending(35,now,2)));
        when(transitions.claim(35,now)).thenReturn(claimed);when(contexts.load(35)).thenReturn(context);
        when(runner.report(eq(context),anyLong())).thenReturn(content());
        when(transitions.markSucceeded(eq(claimed),anyString(),eq(now))).thenAnswer(invocation->{done.countDown();return true;});
        var executor=new ReportExecutor(reports,transitions,contexts,runner,new ObjectMapper(),Clock.fixed(now,ZoneOffset.UTC),new SimulationProperties());
        try {executor.markReady();executor.poll();assertTrue(done.await(3,TimeUnit.SECONDS));verify(runner).report(context,120000);}
        finally {executor.close();}
    }
}
