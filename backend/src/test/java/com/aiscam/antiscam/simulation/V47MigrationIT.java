package com.aiscam.antiscam.simulation;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V47 knowledge base: one rule per scene title, one source per intervention, and a frozen per-session snapshot.
 * Runs on an isolated local MySQL schema, never the application database.
 */
@EnabledIfEnvironmentVariable(named = "V14_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/v14_it.*")
class V47MigrationIT {
    @Test
    void addsRulesSourcesAndSnapshotWithoutTouchingOldSessions() throws Exception {
        String url = System.getenv("V14_TEST_JDBC_URL").replace("/v14_it", "/v47_rules");
        Flyway.configure().dataSource(url, "root", "").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, "root", "").target("46").load().migrate();
        long sessionId;
        try (var c = DriverManager.getConnection(url, "root", "")) {
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (501,'v47-admin','test','ADMIN')");
            sessionId = insertSession(c, 501);
            execute(c, "INSERT INTO simulation_round(id,session_id,round_no) VALUES (601," + sessionId + ",1)");
            execute(c, """
                INSERT INTO simulation_scammer_draft(id,session_id,round_id,round_no,revision_no,status,original_content,
                  draft_content,created_by)
                VALUES (701,%d,601,1,0,'ADOPTED','原始台词','最终台词',501)
                """.formatted(sessionId));
            execute(c, """
                INSERT INTO simulation_intervention(id,session_id,round_id,round_no,draft_id,status,mode,scene_code,
                  admin_user_id,original_content,final_content)
                VALUES (801,%d,601,1,701,'PENDING','ADMIN_TEST','TASK_REBATE',501,'原始台词','最终台词')
                """.formatted(sessionId));
        }

        var latest = Flyway.configure().dataSource(url, "root", "").load();
        latest.migrate();
        latest.validate();
        assertTrue(Integer.parseInt(latest.info().current().getVersion().getVersion()) >= 47);

        try (var c = DriverManager.getConnection(url, "root", "")) {
            // Old sessions keep a null snapshot; new ones can freeze a JSON array.
            assertTrue(query(c, "SELECT scene_rule_snapshot IS NULL FROM simulation_session WHERE id=" + sessionId)
                    .equals("1"));
            execute(c, "UPDATE simulation_session SET scene_rule_snapshot='[]' WHERE id=" + sessionId);
            assertEquals("[]", query(c, "SELECT scene_rule_snapshot FROM simulation_session WHERE id=" + sessionId));
            execute(c, "UPDATE simulation_session SET scene_rule_snapshot='[{\"ruleId\":\"1\",\"title\":\"t\","
                    + "\"ruleText\":\"正文\",\"version\":1}]' WHERE id=" + sessionId);
            assertEquals("1", query(c, "SELECT JSON_LENGTH(scene_rule_snapshot) FROM simulation_session WHERE id="
                    + sessionId));
            assertThrows(SQLException.class, () -> execute(c,
                    "UPDATE simulation_session SET scene_rule_snapshot='{\"ruleId\":\"1\"}' WHERE id=" + sessionId),
                    "the snapshot is an array, never a single object");

            execute(c, """
                INSERT INTO scene_rule(id,scenario_code,title,rule_text,version_no,status,created_by)
                VALUES (901,'TASK_REBATE','先解释费用再要求垫付','先把费用构成解释清楚，再提出垫付要求',3,'ENABLED',501)
                """);
            execute(c, """
                INSERT INTO scene_rule(id,scenario_code,title,rule_text,version_no,status,created_by)
                VALUES (902,'TASK_REBATE','用进度截图建立可信度','用可核对的进度截图说明返利节奏',1,'ENABLED',501)
                """);
            execute(c, """
                INSERT INTO scene_rule(id,scenario_code,title,rule_text,version_no,status,created_by)
                VALUES (903,'FAKE_INVESTMENT','同一规则不得跨场景重名','同场景标题唯一即可',1,'DISABLED',501)
                """);
            assertThrows(SQLException.class, () -> execute(c, """
                INSERT INTO scene_rule(id,scenario_code,title,rule_text,version_no,status,created_by)
                VALUES (904,'TASK_REBATE','先解释费用再要求垫付','重复标题',1,'ENABLED',501)
                """), "one rule per scene title");
            assertThrows(SQLException.class, () -> execute(c,
                    "UPDATE scene_rule SET status='PAUSED' WHERE id=901"));
            assertEquals("901,902", query(c, "SELECT GROUP_CONCAT(id ORDER BY version_no DESC, id ASC) FROM scene_rule "
                    + "WHERE scenario_code='TASK_REBATE' AND status='ENABLED'"));

            execute(c, "INSERT INTO scene_rule_source(rule_id,intervention_id,admin_user_id) VALUES (901,801,501)");
            // The service inserts a source only when it is not linked yet; the primary key makes a retry a no-op.
            execute(c, "INSERT IGNORE INTO scene_rule_source(rule_id,intervention_id,admin_user_id) VALUES (901,801,501)");
            assertEquals("1", query(c, "SELECT COUNT(*) FROM scene_rule_source WHERE rule_id=901"));
            assertThrows(SQLException.class, () -> execute(c,
                    "INSERT INTO scene_rule_source(rule_id,intervention_id,admin_user_id) VALUES (902,801,501)"),
                    "one source belongs to exactly one rule");
            assertThrows(SQLException.class, () -> execute(c,
                    "INSERT INTO scene_rule_source(rule_id,intervention_id,admin_user_id) VALUES (902,999,501)"));
        }
    }

    private static long insertSession(Connection c, long userId) throws SQLException {
        String requestId = UUID.randomUUID().toString();
        execute(c, """
            INSERT INTO simulation_session(user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
              snapshot_schema_version,scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,
              narrator_prompt_id,report_prompt_id,event_prompt_id,runtime_snapshot,status,current_round_no,started_at,
              received_at,timing_evidence,intervention_mode)
            SELECT %d,'%s',1,1,1,4,'{}','{}','{}',
              (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='EVENT' AND is_active=1),
              '{"ruleset_version":"PRD-V3.5","trust_config":{"trust_ruleset_version":"trust-v3"}}',
              'RUNNING',0,NOW(3),NOW(3),'{}','ADMIN_TEST'
            """.formatted(userId, requestId));
        return queryLong(c, "SELECT id FROM simulation_session WHERE start_request_id='" + requestId + "'");
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
