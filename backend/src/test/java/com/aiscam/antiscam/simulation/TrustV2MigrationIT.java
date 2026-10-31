package com.aiscam.antiscam.simulation;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Run against two empty schemas on an isolated local MySQL, never the application's database. */
@EnabledIfEnvironmentVariable(named="TRUST_V2_TEST_JDBC_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/trust_v2_upgrade.*")
class TrustV2MigrationIT {
    @Test void upgradesSeedsAndProgressWithoutRewritingHistoryOrAdministratorValues() throws Exception {
        String url = System.getenv("TRUST_V2_TEST_JDBC_URL");
        Flyway.configure().dataSource(url,"root","").target("37").load().migrate();
        try (var c = DriverManager.getConnection(url,"root","")) {
            execute(c,"UPDATE victim_scenario_profile SET initial_trust_score=43 WHERE victim_profile_id=1 AND scenario_code='FAKE_INVESTMENT'");
            execute(c,"INSERT INTO sys_user(id,username,password_hash,role) VALUES (201,'v2-migration','test','USER')");
            execute(c,"""
                INSERT INTO simulation_session(id,user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
                  scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,narrator_prompt_id,report_prompt_id,
                  runtime_snapshot,status,current_round_no,started_at,received_at,timing_evidence,deadline_at)
                SELECT 201,201,UUID(),1,1,1,'{}','{}','{"initial_trust_score":50}',
                  (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
                  '{"trust_config":{"trust_ruleset_version":"trust-v1"}}','RUNNING',0,NOW(3),NOW(3),'{}',TIMESTAMPADD(SECOND,600,NOW(3))
                """);
            execute(c,"INSERT INTO simulation_round(id,session_id,round_no) VALUES (201,201,1)");
            execute(c,"""
                INSERT INTO simulation_trust_event(session_id,round_no,event_type,subject_key,base_delta,factor,delta,score_before,score_after,evidence_source,evidence_quote)
                VALUES (201,1,'NEW_EFFECTIVE_EXPLANATION','结算',3,1,3,50,53,'SYSTEM','旧事件原文')
                """);
            String frozen = query(c,"SELECT CONCAT(victim_snapshot,runtime_snapshot,deadline_at) FROM simulation_session WHERE id=201");
            String oldPrompt = query(c,"SELECT scammer_prompt_id FROM simulation_session WHERE id=201");
            var latest = Flyway.configure().dataSource(url,"root","").load();
            latest.migrate(); latest.validate();
            assertTrue(Integer.parseInt(latest.info().current().getVersion().getVersion())>=44);
            assertEquals(frozen,query(c,"SELECT CONCAT(victim_snapshot,runtime_snapshot,deadline_at) FROM simulation_session WHERE id=201"));
            assertEquals(oldPrompt,query(c,"SELECT scammer_prompt_id FROM simulation_session WHERE id=201"));
            assertEquals("40,70,85",query(c,"SELECT CONCAT_WS(',',trust_threshold_accept,trust_threshold_small,trust_threshold_large) FROM simulation_policy"));
            assertEquals("50,1.20,1.00",query(c,"SELECT CONCAT_WS(',',initial_trust_score,positive_factor,negative_factor) FROM victim_scenario_profile WHERE victim_profile_id=1 AND scenario_code='TASK_REBATE'"));
            assertEquals("43,0.60,1.40",query(c,"SELECT CONCAT_WS(',',initial_trust_score,positive_factor,negative_factor) FROM victim_scenario_profile WHERE victim_profile_id=1 AND scenario_code='FAKE_INVESTMENT'"));
            assertEquals("45,1.00,1.20",query(c,"SELECT CONCAT_WS(',',initial_trust_score,positive_factor,negative_factor) FROM victim_scenario_profile WHERE victim_profile_id=3 AND scenario_code='TASK_REBATE'"));
            assertEquals("结算,50,53,旧事件原文",query(c,"SELECT CONCAT_WS(',',progress_key,score_before,score_after,evidence_quote) FROM simulation_trust_event"));
            execute(c,"""
                INSERT INTO simulation_trust_event(session_id,round_no,event_type,subject_key,progress_key,base_delta,factor,delta,score_before,score_after,evidence_source,evidence_quote)
                VALUES (201,1,'DOUBT_RESOLVED','结算','费用承担方',4,1,4,53,57,'SYSTEM','新进展原文')
                """);
            assertEquals("5",query(c,"SELECT COUNT(*) FROM agent_prompt WHERE is_active=1"));
            execute(c,"""
                UPDATE simulation_session SET runtime_snapshot=JSON_SET(runtime_snapshot,
                  '$.trust_config.trust_ruleset_version','trust-v2','$.pacing_config.max_active_duration_ms',1800000),
                  deadline_at=TIMESTAMPADD(MICROSECOND,1800000000,started_at) WHERE id=201
                """);
            // V44 replaced the fixed 600 s/1800 s duration check with "legacy sessions keep a non-null deadline";
            // the frozen duration itself is only used at read time.
            assertThrows(SQLException.class,()->execute(c,"UPDATE simulation_session SET deadline_at=NULL WHERE id=201"));
            execute(c,"UPDATE simulation_session SET paused_total_ms=1000,deadline_at=TIMESTAMPADD(MICROSECOND,1801000000,started_at) WHERE id=201");
        }
        String custom = url.replace("/trust_v2_upgrade", "/trust_v2_custom");
        Flyway.configure().dataSource(custom,"root","").target("37").load().migrate();
        try (var c = DriverManager.getConnection(custom,"root","")) {
            execute(c,"UPDATE simulation_policy SET trust_threshold_accept=40,trust_threshold_small=65,trust_threshold_large=85,row_version=1");
            Flyway.configure().dataSource(custom,"root","").load().migrate();
            assertEquals("40,65,85,1",query(c,"SELECT CONCAT_WS(',',trust_threshold_accept,trust_threshold_small,trust_threshold_large,row_version) FROM simulation_policy"));
        }
    }
    private void execute(Connection c,String sql) throws SQLException { try (var s=c.createStatement()) { s.execute(sql); } }
    private String query(Connection c,String sql) throws SQLException {
        try (var s=c.createStatement();var r=s.executeQuery(sql)) { assertTrue(r.next()); return r.getString(1); }
    }
}
