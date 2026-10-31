package com.aiscam.antiscam.simulation;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V46 intervention persistence. Runs on an isolated local MySQL schema, never the application database.
 */
@EnabledIfEnvironmentVariable(named = "V14_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/v14_it.*")
class V46MigrationIT {
    @Test
    void addsDraftsAndInterventionsWithoutTouchingOldRows() throws Exception {
        String url = System.getenv("V14_TEST_JDBC_URL").replace("/v14_it", "/v46_intervention");
        Flyway.configure().dataSource(url, "root", "").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, "root", "").target("45").load().migrate();
        long sessionId;
        long roundId;
        try (var c = DriverManager.getConnection(url, "root", "")) {
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (401,'v46-admin','test','ADMIN')");
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (402,'v46-user','test','USER')");
            sessionId = insertSession(c, 401);
            roundId = insertRound(c, 401, sessionId);
            execute(c, """
                INSERT INTO simulation_report(session_id,status,attempt_no,task_token,queued_at,started_at,finished_at,
                  content_schema_version,content_json)
                VALUES (%d,'SUCCEEDED',1,'%s',NOW(3),NOW(3),NOW(3),3,'{"contentSchemaVersion":3,"summary":"旧报告正文"}')
                """.formatted(sessionId, UUID.randomUUID()));
        }

        var latest = Flyway.configure().dataSource(url, "root", "").load();
        latest.migrate();
        latest.validate();
        assertTrue(Integer.parseInt(latest.info().current().getVersion().getVersion()) >= 46);

        try (var c = DriverManager.getConnection(url, "root", "")) {
            // Old sessions default to NONE and old reports are untouched.
            assertEquals("NONE", query(c, "SELECT intervention_mode FROM simulation_session WHERE id=" + sessionId));
            assertEquals("3,旧报告正文", query(c, "SELECT CONCAT_WS(',',content_schema_version,"
                    + "JSON_UNQUOTE(JSON_EXTRACT(content_json,'$.summary'))) FROM simulation_report WHERE session_id=" + sessionId));
            execute(c, "UPDATE simulation_session SET intervention_mode='ADMIN_TEST' WHERE id=" + sessionId);
            assertEquals("ADMIN_TEST", query(c, "SELECT intervention_mode FROM simulation_session WHERE id=" + sessionId));
            assertThrows(SQLException.class, () -> execute(c,
                    "UPDATE simulation_session SET intervention_mode='DEMO' WHERE id=" + sessionId));

            // The review phase is accepted and unknown phases are rejected.
            execute(c, "UPDATE simulation_round SET phase='SCAMMER_REVIEW' WHERE id=" + roundId);
            assertEquals("SCAMMER_REVIEW", query(c, "SELECT phase FROM simulation_round WHERE id=" + roundId));
            assertThrows(SQLException.class, () -> execute(c,
                    "UPDATE simulation_round SET phase='ADMIN_REVIEW' WHERE id=" + roundId));

            long draftId = insertDraft(c, sessionId, roundId, 0, "DRAFT");
            assertEquals("DRAFT,原始台词,原始台词", query(c, "SELECT CONCAT_WS(',',status,original_content,draft_content) "
                    + "FROM simulation_scammer_draft WHERE id=" + draftId));
            assertThrows(SQLException.class, () -> insertDraft(c, sessionId, roundId, 1, "DRAFT"),
                    "only one active DRAFT per round");
            // A decided draft no longer occupies the active slot.
            execute(c, "UPDATE simulation_scammer_draft SET status='ADOPTED' WHERE id=" + draftId);
            long second = insertDraft(c, sessionId, roundId, 1, "DRAFT");
            execute(c, "UPDATE simulation_scammer_draft SET status='DISCARDED' WHERE id=" + second);
            insertDraft(c, sessionId, roundId, 2, "DRAFT");

            long messageId = insertMessage(c, roundId);
            long interventionId = insertIntervention(c, sessionId, roundId, draftId, messageId);
            assertEquals("LINKED,TASK_REBATE", query(c, "SELECT CONCAT_WS(',',status,scene_code) "
                    + "FROM simulation_intervention WHERE id=" + interventionId));
            assertThrows(SQLException.class, () -> insertIntervention(c, sessionId, roundId, draftId, null),
                    "one draft produces at most one intervention");
            assertThrows(SQLException.class, () -> execute(c, """
                INSERT INTO simulation_intervention(session_id,round_id,round_no,draft_id,final_message_id,status,mode,
                  scene_code,admin_user_id,original_content,final_content)
                VALUES (%d,%d,1,%d,%d,'LINKED','ADMIN_TEST','TASK_REBATE',401,'原始','最终')
                """.formatted(sessionId, roundId, draftId, messageId)), "one formal message links one intervention");
            assertThrows(SQLException.class, () -> execute(c, """
                INSERT INTO simulation_intervention(session_id,round_id,round_no,draft_id,status,mode,scene_code,
                  admin_user_id,original_content,final_content)
                VALUES (%d,%d,1,%d,'PENDING','ADMIN_TEST','FAKE_LOAN',401,'原始','最终')
                """.formatted(sessionId, roundId, draftId)));
            assertThrows(SQLException.class, () -> execute(c, """
                INSERT INTO simulation_intervention(session_id,round_id,round_no,draft_id,status,mode,scene_code,
                  admin_user_id,original_content,final_content)
                VALUES (%d,%d,1,%d,'PENDING','USER','TASK_REBATE',401,'原始','最终')
                """.formatted(sessionId, roundId, draftId)));
        }
    }

    private static long insertSession(Connection c, long userId) throws SQLException {
        String requestId = UUID.randomUUID().toString();
        execute(c, """
            INSERT INTO simulation_session(user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
              snapshot_schema_version,scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,
              narrator_prompt_id,report_prompt_id,event_prompt_id,runtime_snapshot,status,current_round_no,started_at,
              received_at,timing_evidence)
            SELECT %d,'%s',1,1,1,4,'{}','{}','{}',
              (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='EVENT' AND is_active=1),
              '{"ruleset_version":"PRD-V3.5","trust_config":{"trust_ruleset_version":"trust-v3"}}',
              'RUNNING',0,NOW(3),NOW(3),'{}'
            """.formatted(userId, requestId));
        return queryLong(c, "SELECT id FROM simulation_session WHERE start_request_id='" + requestId + "'");
    }

    private static long insertRound(Connection c, long id, long sessionId) throws SQLException {
        execute(c, "INSERT INTO simulation_round(id,session_id,round_no) VALUES (" + id + "," + sessionId + ",1)");
        return id;
    }

    private static long insertDraft(Connection c, long sessionId, long roundId, int revisionNo, String status)
            throws SQLException {
        execute(c, """
            INSERT INTO simulation_scammer_draft(session_id,round_id,round_no,revision_no,status,original_content,
              original_action,draft_content,draft_action,created_by)
            VALUES (%d,%d,1,%d,'%s','原始台词','原始动作','原始台词','原始动作',401)
            """.formatted(sessionId, roundId, revisionNo, status));
        return queryLong(c, "SELECT MAX(id) FROM simulation_scammer_draft WHERE round_id=" + roundId);
    }

    private static long insertMessage(Connection c, long roundId) throws SQLException {
        execute(c, """
            INSERT INTO simulation_message(round_id,speaker_role,content,action_text,presentation_status)
            VALUES (%d,'SCAMMER','最终台词','最终动作','PENDING')
            """.formatted(roundId));
        return queryLong(c, "SELECT MAX(id) FROM simulation_message WHERE round_id=" + roundId);
    }

    private static long insertIntervention(Connection c, long sessionId, long roundId, long draftId, Long messageId)
            throws SQLException {
        execute(c, """
            INSERT INTO simulation_intervention(session_id,round_id,round_no,draft_id,final_message_id,status,mode,
              scene_code,admin_user_id,original_content,final_content)
            VALUES (%d,%d,1,%d,%s,'LINKED','ADMIN_TEST','TASK_REBATE',401,'原始台词','最终台词')
            """.formatted(sessionId, roundId, draftId, messageId == null ? "NULL" : messageId.toString()));
        return queryLong(c, "SELECT MAX(id) FROM simulation_intervention WHERE draft_id=" + draftId);
    }

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
