package com.aiscam.antiscam.simulation;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V44 core structure. Runs against an isolated local MySQL schema, never the application database:
 * {@code V14_TEST_JDBC_URL=jdbc:mysql://127.0.0.1:<port>/v14_it}; the upgrade schema is derived from it.
 */
@EnabledIfEnvironmentVariable(named = "V14_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/v14_it.*")
class V44MigrationIT {
    @Test
    void addsV14StructureWithoutRewritingHistoryOrLooseningOutcomeChecks() throws Exception {
        String url = System.getenv("V14_TEST_JDBC_URL").replace("/v14_it", "/v44_upgrade");
        Flyway.configure().dataSource(url, "root", "").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, "root", "").target("43").load().migrate();
        long legacyId;
        try (var c = DriverManager.getConnection(url, "root", "")) {
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (201,'v44-migration','test','USER')");
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (202,'v44-running','test','USER')");
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (203,'v44-outcome','test','USER')");
            legacyId = insertLegacySession(c);
            for (int round = 1; round <= 8; round++) {
                execute(c, "INSERT INTO simulation_round(id,session_id,round_no) VALUES (" + (300 + round) + "," + legacyId + "," + round + ")");
            }
            execute(c, """
                INSERT INTO simulation_trust_event(session_id,round_no,event_type,subject_key,progress_key,
                  base_delta,factor,delta,score_before,score_after,evidence_source,evidence_quote)
                VALUES (%d,3,'DECISIVE_COUNTEREVIDENCE','旧反证','旧反证进展',-10,1.40,-14,60,46,'SYSTEM','旧反证原文')
                """.formatted(legacyId));
            insertSucceededReport(c, legacyId, 3, "{\"contentSchemaVersion\":3,\"summary\":\"旧报告正文\"}");
        }
        String before = null;
        try (var c = DriverManager.getConnection(url, "root", "")) {
            before = query(c, "SELECT CONCAT_WS('|',progress_key,score_before,score_after,evidence_quote) "
                    + "FROM simulation_trust_event WHERE session_id=" + legacyId);
        }

        var latest = Flyway.configure().dataSource(url, "root", "").load();
        latest.migrate();
        latest.validate();
        assertTrue(Integer.parseInt(latest.info().current().getVersion().getVersion()) >= 44,
                "V44 is the first new version; later phases may only add versions above it");

        try (var c = DriverManager.getConnection(url, "root", "")) {
            assertEquals(before, query(c, "SELECT CONCAT_WS('|',progress_key,score_before,score_after,evidence_quote) "
                    + "FROM simulation_trust_event WHERE session_id=" + legacyId));
            assertEquals("旧反证,旧反证进展,,", query(c, "SELECT CONCAT_WS(',',subject_key,progress_key,"
                    + "COALESCE(counterevidence_level,''),COALESCE(target_score,'')) "
                    + "FROM simulation_trust_event WHERE session_id=" + legacyId));
            assertEquals("3,旧报告正文", query(c, "SELECT CONCAT_WS(',',content_schema_version,"
                    + "JSON_UNQUOTE(JSON_EXTRACT(content_json,'$.summary'))) FROM simulation_report WHERE session_id=" + legacyId));
            assertEquals("PRD-V3.4,8,DEFRAUDED,,", query(c, "SELECT CONCAT_WS(',',"
                    + "JSON_UNQUOTE(JSON_EXTRACT(runtime_snapshot,'$.ruleset_version')),current_round_no,outcome_code,"
                    + "COALESCE(winner,''),COALESCE(final_trust_score,'')) FROM simulation_session WHERE id=" + legacyId));
            assertEquals("40,70,85", query(c, "SELECT CONCAT_WS(',',trust_threshold_accept,trust_threshold_small,"
                    + "trust_threshold_large) FROM simulation_policy"));

            // Snapshot 4 carries no business deadline and may store a schema 4 report.
            long runningId = insertSession(c, 202, 4);
            assertEquals("4,1", query(c, "SELECT CONCAT_WS(',',snapshot_schema_version,deadline_at IS NULL) "
                    + "FROM simulation_session WHERE id=" + runningId));
            insertSucceededReport(c, runningId, 4, "{\"contentSchemaVersion\":4,\"winning\":\"SCAMMER\"}");
            // Short narration fields are new and optional; the full fields keep their own values.
            insertRound(c, 401, runningId, 1);
            insertRound(c, 402, runningId, 2);
            assertEquals(",,,,", query(c, "SELECT CONCAT_WS(',',COALESCE(method_short,''),COALESCE(intent_short,''),"
                    + "COALESCE(method_analysis,''),COALESCE(speech_analysis,''),COALESCE(analysis_saved_at,'')) "
                    + "FROM simulation_round WHERE id=401"));
            execute(c, "UPDATE simulation_round SET fraud_stage='试探期',speech_analysis='第2轮台词',"
                    + "method_analysis='第2轮手法',method_short='冒充客服',intent_short='借平台身份让你先垫付小额验证金',"
                    + "victim_psychology='半信半疑',risk_score=50,risk_reason='小额试探',actual_fraud_occurred=1,"
                    + "goal_achieved=0,cooperation_stopped=0,evidence_json='[]',analysis_saved_at=NOW(3) WHERE id=402");
            assertEquals("冒充客服,借平台身份让你先垫付小额验证金,第2轮手法,第2轮台词",
                    query(c, "SELECT CONCAT_WS(',',method_short,intent_short,method_analysis,speech_analysis) "
                            + "FROM simulation_round WHERE id=402"));

            // Legal 15-round outcome combinations.
            long won = insertSession(c, 203, 4);
            updateOutcome(c, won, "SCAMMER_WIN", "SCAMMER", 80, 15);
            assertEquals("SCAMMER_WIN,SCAMMER,80", query(c, "SELECT CONCAT_WS(',',outcome_code,winner,final_trust_score) "
                    + "FROM simulation_session WHERE id=" + won));
            updateOutcome(c, won, "VICTIM_WIN", "VICTIM", 30, 15);
            assertEquals("VICTIM_WIN,VICTIM,30", query(c, "SELECT CONCAT_WS(',',outcome_code,winner,final_trust_score) "
                    + "FROM simulation_session WHERE id=" + won));
            // Old business outcomes keep an empty winner and final score, including an 8-round finish.
            updateOutcome(c, won, "DEFRAUDED", null, null, 8);
            assertEquals("DEFRAUDED,,", query(c, "SELECT CONCAT_WS(',',outcome_code,COALESCE(winner,''),"
                    + "COALESCE(final_trust_score,'')) FROM simulation_session WHERE id=" + won));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "DEFRAUDED", "SCAMMER", 80, 8),
                    "legacy codes must not carry a winner");
            // Reverse assertions: every winner/final/round invariant is explicit, never UNKNOWN.
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "SCAMMER_WIN", "VICTIM", 80, 15));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "VICTIM_WIN", "SCAMMER", 30, 15));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "SCAMMER_WIN", null, 80, 15));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "VICTIM_WIN", null, 30, 15));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "SCAMMER_WIN", "SCAMMER", null, 15));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "VICTIM_WIN", "VICTIM", null, 15));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "SCAMMER_WIN", "SCAMMER", 101, 15));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "VICTIM_WIN", "VICTIM", 101, 15));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "SCAMMER_WIN", "SCAMMER", 80, 14));
            assertThrows(SQLException.class, () -> updateOutcome(c, won, "VICTIM_WIN", "VICTIM", 30, 14));

            // New trust event columns and type guard.
            execute(c, """
                INSERT INTO simulation_trust_event(session_id,round_no,event_type,subject_key,progress_key,
                  counterevidence_level,target_score,constraint_active,base_delta,factor,delta,score_before,score_after,
                  evidence_source,evidence_quote)
                VALUES (%d,1,'DECISIVE_COUNTEREVIDENCE','材料日期矛盾','日期第一次矛盾','PARTIAL',30,1,0,1.00,0,60,60,'SYSTEM','日期矛盾原文')
                """.formatted(runningId));
            execute(c, """
                INSERT INTO simulation_trust_event(session_id,round_no,event_type,subject_key,progress_key,
                  base_delta,factor,delta,score_before,score_after,evidence_source,evidence_quote)
                VALUES (%d,2,'COUNTEREVIDENCE_RESOLVED','材料日期矛盾','日期已解释',0,1.00,0,60,60,'SYSTEM','解释原文')
                """.formatted(runningId));
            assertThrows(SQLException.class, () -> execute(c, """
                INSERT INTO simulation_trust_event(session_id,round_no,event_type,subject_key,progress_key,
                  counterevidence_level,base_delta,factor,delta,score_before,score_after,evidence_source,evidence_quote)
                VALUES (%d,2,'NEW_CONTRADICTION','带档位的普通事件','进展','PARTIAL',-6,1.00,-6,60,54,'SYSTEM','原文')
                """.formatted(runningId)), "only decisive events may carry a counterevidence level");
            assertThrows(SQLException.class, () -> execute(c, """
                INSERT INTO simulation_trust_event(session_id,round_no,event_type,subject_key,progress_key,
                  base_delta,factor,delta,score_before,score_after,evidence_source,evidence_quote)
                VALUES (%d,2,'UNKNOWN_EVENT','未知类型','进展',0,1.00,0,60,60,'SYSTEM','原文')
                """.formatted(runningId)));
        }
    }

    private void insertSucceededReport(Connection c, long sessionId, int schemaVersion, String contentJson)
            throws SQLException {
        execute(c, """
            INSERT INTO simulation_report(session_id,status,attempt_no,task_token,queued_at,started_at,finished_at,
              content_schema_version,content_json)
            VALUES (%d,'SUCCEEDED',1,'%s',NOW(3),NOW(3),NOW(3),%d,'%s')
            """.formatted(sessionId, UUID.randomUUID().toString(), schemaVersion, contentJson));
    }

    private long insertLegacySession(Connection c) throws SQLException {
        String requestId = UUID.randomUUID().toString();
        execute(c, """
            INSERT INTO simulation_session(user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
              snapshot_schema_version,scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,
              narrator_prompt_id,report_prompt_id,runtime_snapshot,status,current_round_no,started_at,received_at,
              timing_evidence,deadline_at,paused_total_ms,ended_at,outcome_code,outcome_reason,
              initial_trust_score,current_trust_score,trust_positive_factor,trust_negative_factor,trust_small_cap_yuan,
              trust_threshold_accept,trust_threshold_small,trust_threshold_large)
            SELECT 201,'%s',1,1,1,3,'{}','{}','{"initial_trust_score":50}',
              (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
              '{"ruleset_version":"PRD-V3.4","trust_config":{"trust_ruleset_version":"trust-v2"}}',
              'FINISHED',8,TIMESTAMPADD(SECOND,-600,NOW(3)),TIMESTAMPADD(SECOND,-601,NOW(3)),'{}',
              NOW(3),0,NOW(3),'DEFRAUDED','小额投入后停止',50,46,1.20,1.40,200,40,70,85
            """.formatted(requestId));
        return queryLong(c, "SELECT id FROM simulation_session WHERE start_request_id='" + requestId + "'");
    }

    private long insertSession(Connection c, long userId, int snapshotVersion) throws SQLException {
        String requestId = UUID.randomUUID().toString();
        String deadline = snapshotVersion >= 4 ? "NULL" : "TIMESTAMPADD(SECOND,600,NOW(3))";
        execute(c, """
            INSERT INTO simulation_session(user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
              snapshot_schema_version,scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,
              narrator_prompt_id,report_prompt_id,runtime_snapshot,status,current_round_no,started_at,received_at,
              timing_evidence,deadline_at)
            SELECT %d,'%s',1,1,1,%d,'{}','{}','{}',
              (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
              '{"ruleset_version":"PRD-V3.5","trust_config":{"trust_ruleset_version":"trust-v3"}}',
              'RUNNING',0,NOW(3),NOW(3),'{}',%s
            """.formatted(userId, requestId, snapshotVersion, deadline));
        return queryLong(c, "SELECT id FROM simulation_session WHERE start_request_id='" + requestId + "'");
    }

    private void updateOutcome(Connection c, long sessionId, String outcome, String winner, Integer finalScore, int roundNo)
            throws SQLException {
        execute(c, "UPDATE simulation_session SET outcome_code=" + quote(outcome) + ",winner=" + quote(winner)
                + ",final_trust_score=" + (finalScore == null ? "NULL" : finalScore) + ",current_round_no=" + roundNo
                + ",ended_at=NOW(3),outcome_reason='结论说明',status='FINISHED' WHERE id=" + sessionId);
    }

    private void insertRound(Connection c, long id, long sessionId, int roundNo) throws SQLException {
        execute(c, "INSERT INTO simulation_round(id,session_id,round_no) VALUES (" + id + "," + sessionId + "," + roundNo + ")");
    }

    private static String quote(String value) { return value == null ? "NULL" : "'" + value + "'"; }

    private static void execute(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) { s.execute(sql); }
    }

    private static String query(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) {
            assertTrue(r.next(), "no row for " + sql);
            return r.getString(1);
        }
    }

    private static long queryLong(Connection c, String sql) throws SQLException {
        String value = query(c, sql);
        assertNotNull(value, "no value for " + sql);
        return Long.parseLong(value);
    }
}
