package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import com.aiscam.antiscam.simulation.domain.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "V14_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/v14_it.*")
class V50DraftFundsIT {
    @Test void migrationRecoversRegenerationAndHibernateRoundTripsFunds() throws Exception {
        String url = System.getenv("V14_TEST_JDBC_URL").replace("v14_it", "v50_check_" + UUID.randomUUID().toString().replace("-", ""));
        String user = System.getenv().getOrDefault("V14_TEST_DB_USER", "root");
        String password = System.getenv().getOrDefault("V14_TEST_DB_PASSWORD", "");
        Flyway.configure().dataSource(url,user,password).target("49").load().migrate();
        try (var c = DriverManager.getConnection(url,user,password); var s = c.createStatement()) {
            s.execute("INSERT INTO sys_user(id,username,password_hash,role) VALUES (901,'v50-admin','test','ADMIN')");
            s.execute("""
                INSERT INTO simulation_session(id,user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
                  snapshot_schema_version,scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,
                  narrator_prompt_id,report_prompt_id,runtime_snapshot,status,current_round_no,started_at,received_at,timing_evidence)
                SELECT 901,901,UUID(),1,1,1,4,'{"scenario_code":"TASK_REBATE"}','{}','{}',
                  (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
                  '{}','RUNNING',2,NOW(3),NOW(3),'{}'
                """);
            s.execute("INSERT INTO simulation_round(id,session_id,round_no) VALUES (901,901,1),(902,901,2)");
            s.execute("""
                INSERT INTO simulation_message(round_id,speaker_role,content,presentation_status) VALUES
                  (901,'SCAMMER','先看说明','PENDING'),(901,'VICTIM','怎么操作','PENDING'),
                  (902,'SCAMMER','采用的新台词','PENDING')
                """);
            s.execute("""
                INSERT INTO simulation_scammer_draft(id,session_id,round_id,round_no,revision_no,status,
                  original_content,draft_content,created_by,guidance_text) VALUES
                  (901,901,902,2,0,'DISCARDED','第一版AI原稿','第一版AI原稿',901,NULL),
                  (902,901,902,2,1,'ADOPTED','采用的新台词','采用的新台词',901,'调整方向')
                """);
        }
        Flyway.configure().dataSource(url,user,password).load().migrate();
        try (var c = DriverManager.getConnection(url,user,password); var s = c.createStatement();
                var r = s.executeQuery("SELECT original_content,final_content,previous_context,status FROM simulation_intervention WHERE session_id=901")) {
            assertTrue(r.next());
            assertEquals("第一版AI原稿",r.getString(1));
            assertEquals("采用的新台词",r.getString(2));
            assertEquals("骗子：先看说明\n受害者：怎么操作",r.getString(3));
            assertEquals("ADOPTED",r.getString(4));
            assertFalse(r.next());
        }
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url",url).applySetting("hibernate.connection.username",user)
                .applySetting("hibernate.connection.password",password).applySetting("hibernate.hbm2ddl.auto","validate")
                .build();
        var action = new FundAction(FundFlowType.REWARD,FundPurpose.REWARD,100,2,SpeakerRole.SCAMMER,
                "已发放100元",1,null,null,null);
        try (var factory = new MetadataSources(registry).addAnnotatedClass(SimulationScammerDraft.class)
                .buildMetadata().buildSessionFactory()) {
            try (var session = factory.openSession()) {
                var tx = session.beginTransaction();
                var draft = session.find(SimulationScammerDraft.class,902L);
                assertTrue(draft.getFundActions().isEmpty());
                draft.setFunds(100,List.of(action));
                tx.commit();
            }
            try (var session = factory.openSession()) {
                var draft = session.find(SimulationScammerDraft.class,902L);
                assertEquals(100,draft.getClaimedRefundYuan());
                assertEquals(List.of(action),draft.getFundActions());
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
