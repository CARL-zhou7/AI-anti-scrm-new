package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ImpersonationIdentityContextTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConfigurationSnapshots snapshots = new ConfigurationSnapshots(mapper);
    private String snapshot(int age, String gender, String relationship) throws Exception {
        return mapper.writeValueAsString(Map.of("age",age,"gender",gender,"identity","普通居民",
                "description","生活背景","family_status","家庭背景","available_funds_yuan",3000,
                "initial_trust_score",65,"selected_scenario_code","IMPERSONATION_LOAN",
                "victim_scenario_profiles",List.of(Map.of("scenario_code","IMPERSONATION_LOAN",
                        "contact_background",relationship,"shared_experiences","既有往来","positive_factor",1.6))));
    }
    private String template(String path) throws Exception {
        try(var in=getClass().getResourceAsStream("/prompts/"+path+".txt")) {
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    @Test void newFatherSonSceneFreezesSharedSurnameWithoutChangingOtherRelationships() throws Exception {
        var all = Arrays.stream(ScenarioCode.values()).map(code ->
                new com.aiscam.antiscam.content.domain.VictimScenarioProfileValues(code.name(),
                        "需求", "经历", "依据", "核实", "边界", "停止", "昨天", "咨询", "共同经历",
                        code == ScenarioCode.IMPERSONATION_LOAN ? "联系者声称是外地工作的儿子" : "群中认识的联系人",
                        "信任", 65, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, 2000)).toList();
        var profile = new com.aiscam.antiscam.content.domain.VictimProfile(
                new com.aiscam.antiscam.content.domain.VictimProfileValues("周国良",66,"男","退休职工","描述",
                        "与妻子共同生活，儿子在外地工作","养老金",3000,20000,5000,10000,5000,"预留","谨慎",
                        "家中","查看消息",com.aiscam.antiscam.content.domain.Level.MEDIUM,
                        com.aiscam.antiscam.content.domain.Level.MEDIUM,"关心亲人","a","b",all));
        org.springframework.test.util.ReflectionTestUtils.setField(profile,"id",1L);
        String frozen = snapshots.victim(profile, ScenarioCode.IMPERSONATION_LOAN);
        String own = snapshots.victimVisibleText(frozen), publicFacts = snapshots.scammerVisibleText(frozen);
        for (String text : List.of(own,publicFacts)) {
            assertTrue(text.contains("父亲姓名为周国良"));
            assertTrue(text.contains("儿子随父姓"));
            assertTrue(text.contains("儿子全名未设定"));
        }
        assertFalse(publicFacts.contains("养老金"));
        assertFalse(snapshots.scammerVisibleText(snapshots.victim(profile, ScenarioCode.FAKE_INVESTMENT)).contains("周国良"));
        assertFalse(snapshots.victimVisibleText(snapshot(23,"女","联系者声称是大学同学许宁")).contains("儿子随父姓"));
    }

    @Test void identityContradictionsCanEndEarlyButTemporaryWaitingCannotEndCooperation() throws Exception {
        String victim = template("v16/victim"), narrator = template("v16/narrator"), event = template("v16/event");
        assertTrue(template("v16/scammer").contains("不能用于自我介绍、签名、借款人姓名"));
        assertTrue(victim.contains("应指出具体矛盾并停止转账"));
        assertTrue(victim.contains("在台词中说出来自已收到信息或本人经历的具体理由"));
        assertTrue(narrator.contains("可在前4轮结束"));
        assertTrue(narrator.contains("cooperationStopped=false"));
        assertTrue(event.contains("属于DECISIVE_COUNTEREVIDENCE"));
        assertNotNull(new db.migration.V42__identity_and_response_continuity().getChecksum());
    }

    @Test void publicDemographicsDoNotExposeFundsOrTrustParameters() throws Exception {
        String visible=snapshots.scammerVisibleText(snapshot(66,"男","联系者声称是外地工作的儿子"));
        var json=mapper.readTree(visible);
        assertEquals(66,json.path("age").asInt());
        assertEquals("男",json.path("gender").asText());
        assertEquals("联系者声称是外地工作的儿子",json.path("contactBackground").asText());
        for(String hidden:List.of("available_funds_yuan","3000","initial_trust_score","positive_factor","1.6"))
            assertFalse(visible.contains(hidden),hidden);
        assertNotNull(new db.migration.V26__keep_impersonated_identity_consistent().getChecksum());
    }

    @ParameterizedTest
    @CsvSource({"66,男,联系者声称是外地工作的儿子","23,女,联系者声称是大学同学许宁","38,男,联系者声称是多年朋友刘凯"})
    void allRolesReceiveTheSameFrozenRelationshipAndNewConsistencyInstructions(int age,String gender,String relationship) throws Exception {
        String frozen=snapshot(age,gender,relationship);
        var scene=new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"亲友借款","简介","骗子真实身份陈启明",
                snapshots.victimVisibleText(frozen),"test",template("v12/scammer"),template("v13/victim"),
                template("v10/narrator"),snapshots.scammerVisibleText(frozen),true,template("v3/event"),true);
        var prompts=new EnumMap<AgentRole,String>(AgentRole.class);
        var runner=new AgentRunner(request -> {
            prompts.put(request.role(),request.prompt());
            try {
                return switch(request.role()) {
                    case SCAMMER -> mapper.writeValueAsString(new ScammerTurn("我有件急事想跟你说",null,"接触对方",
                            new ToneHint(Speed.NORMAL,"自然"),null,List.of()));
                    case VICTIM -> mapper.writeValueAsString(new VictimTurn("先说清楚是什么事",null,"尚待确认",
                            new ToneHint(Speed.NORMAL,"自然"),List.of()));
                    case NARRATOR -> mapper.writeValueAsString(new NarratorJudgement("接触","询问","尚无行为","谨慎",25,
                            "没有确认身份",false,false,false,List.of(),null,null,false,null,List.of()));
                    default -> throw new IllegalStateException();
                };
            } catch(Exception e) {throw new IllegalStateException(e);}
        },new SimulationProperties().getLlm());
        var funds=new FundsContext(ScenarioCode.IMPERSONATION_LOAN,3000,9000,0,0,0,List.of());
        runner.scammer(scene,List.of(),1,10000,0,funds);
        var contact=new DialogueLine(1,SpeakerRole.SCAMMER,"我有件急事想跟你说",null,"PRIVATE-NOTE");
        runner.victim(scene,List.of(),contact,1,10000,false,0,funds);
        runner.narrator(scene,List.of(contact,new DialogueLine(1,SpeakerRole.VICTIM,"先说清楚是什么事",null,"内部")),
                1,PriorFacts.none(),10000,0,funds,null,false,List.of());
        assertEquals(3,prompts.size());
        for(String prompt:prompts.values()) {
            assertTrue(prompt.contains(relationship));
            assertFalse(prompt.contains("{{"));
        }
        assertTrue(prompts.get(AgentRole.SCAMMER).contains("受话者年龄/性别"));
        assertTrue(prompts.get(AgentRole.SCAMMER).contains("本场既定身份"));
        assertTrue(prompts.get(AgentRole.VICTIM).contains("不能为了接上话自动把父亲改成儿子"));
        assertFalse(prompts.get(AgentRole.VICTIM).contains("PRIVATE-NOTE"));
        assertTrue(prompts.get(AgentRole.SCAMMER).contains("姓名或姓氏未明确公开"));
        assertTrue(prompts.get(AgentRole.VICTIM).contains("不能照搬这个称呼去称对方"));
        assertTrue(prompts.get(AgentRole.NARRATOR).contains("发言开头的称呼通常指向受话者"));
        assertTrue(prompts.get(AgentRole.NARRATOR).contains("不得自行推断真名、父子同姓或姓名不符"));
    }
    @Test void investmentContactCannotTurnTheVictimsAddressIntoHerOwnName() throws Exception {
        String frozen=snapshot(42,"男","投资群中认识的联系人").replace("IMPERSONATION_LOAN","FAKE_INVESTMENT");
        var profile=mapper.readTree(frozen);
        ((com.fasterxml.jackson.databind.node.ObjectNode)profile).put("name","赵诚");
        frozen=mapper.writeValueAsString(profile);
        var scene=new AgentScene(ScenarioCode.FAKE_INVESTMENT,"虚假投资","简介","女性联系人梁悦",
                snapshots.victimVisibleText(frozen),"test",template("v12/scammer"),template("v13/victim"),
                template("v10/narrator"),snapshots.scammerVisibleText(frozen),true,template("v6/event"),true);
        assertEquals("赵诚",mapper.readTree(snapshots.victimVisibleText(frozen).split("\n",2)[0]).path("name").asText());
        assertFalse(snapshots.scammerVisibleText(frozen).contains("赵诚"));
        var contact=new DialogueLine(6,SpeakerRole.SCAMMER,"张哥，最后一个名额给你留着。",null,"PRIVATE-NOTE");
        var variables=AgentContextAssembler.victim(scene,List.of(),contact,6,100,false);
        variables.put("funds_context","当前可用余额3000元");
        variables.put("received_facts","");
        String prompt=PromptRenderer.render(scene.victimTemplate(),variables);
        assertTrue(prompt.contains("以上是你本人的背景，不是联系人的身份"));
        assertTrue(prompt.contains("联系人发言开头对你的称呼指向你"));
        assertTrue(prompt.contains("对方姓名未介绍时，直接说事或用"));
        assertFalse(prompt.contains("PRIVATE-NOTE"));
        assertFalse(prompt.contains("女性联系人梁悦"));
        assertNotNull(new db.migration.V36__keep_addresses_with_the_correct_speaker().getChecksum());
    }

}
