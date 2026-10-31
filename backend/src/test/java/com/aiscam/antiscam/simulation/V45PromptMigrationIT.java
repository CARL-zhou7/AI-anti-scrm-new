package com.aiscam.antiscam.simulation;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V45 activates the V1.4 prompt generation for SCAMMER/EVENT/NARRATOR only, without touching VICTIM/REPORT or any
 * already pinned session. Runs on an isolated local MySQL schema, never the application database.
 */
@EnabledIfEnvironmentVariable(named = "V14_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/v14_it.*")
class V45PromptMigrationIT {
    @Test
    void activatesOnlyScammerEventNarratorAndKeepsPinnedSessions() throws Exception {
        String url = System.getenv("V14_TEST_JDBC_URL").replace("/v14_it", "/v45_prompts");
        Flyway.configure().dataSource(url, "root", "").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, "root", "").target("44").load().migrate();
        String pinnedIds;
        String victimId;
        String reportId;
        String pinnedContent;
        try (var c = DriverManager.getConnection(url, "root", "")) {
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (301,'v45-migration','test','USER')");
            pinnedIds = query(c, "SELECT CONCAT_WS(',',"
                    + "(SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),"
                    + "(SELECT id FROM agent_prompt WHERE prompt_type='EVENT' AND is_active=1),"
                    + "(SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1))");
            victimId = query(c, "SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1");
            reportId = query(c, "SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1");
            pinnedContent = query(c, "SELECT CONCAT_WS('|',"
                    + "(SELECT LEFT(template_content,80) FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),"
                    + "(SELECT LEFT(template_content,80) FROM agent_prompt WHERE prompt_type='EVENT' AND is_active=1),"
                    + "(SELECT LEFT(template_content,80) FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1))");
            execute(c, """
                INSERT INTO simulation_session(user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
                  snapshot_schema_version,scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,
                  narrator_prompt_id,report_prompt_id,event_prompt_id,runtime_snapshot,status,current_round_no,started_at,
                  received_at,timing_evidence)
                SELECT 301,UUID(),1,1,1,4,'{}','{}','{}',
                  (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
                  (SELECT id FROM agent_prompt WHERE prompt_type='EVENT' AND is_active=1),
                  '{"ruleset_version":"PRD-V3.5","trust_config":{"trust_ruleset_version":"trust-v3"}}',
                  'RUNNING',0,NOW(3),NOW(3),'{}'
                """);
        }

        var latest = Flyway.configure().dataSource(url, "root", "").load();
        latest.migrate();
        latest.validate();
        assertTrue(Integer.parseInt(latest.info().current().getVersion().getVersion()) >= 45);

        try (var c = DriverManager.getConnection(url, "root", "")) {
            assertEquals(pinnedIds, query(c, "SELECT CONCAT_WS(',',scammer_prompt_id,event_prompt_id,narrator_prompt_id) "
                    + "FROM simulation_session WHERE user_id=301"), "the running session keeps its pinned prompt ids");
            assertEquals(pinnedContent, query(c, "SELECT CONCAT_WS('|',"
                    + "(SELECT LEFT(template_content,80) FROM agent_prompt WHERE id=" + pinnedIds.split(",")[0] + "),"
                    + "(SELECT LEFT(template_content,80) FROM agent_prompt WHERE id=" + pinnedIds.split(",")[1] + "),"
                    + "(SELECT LEFT(template_content,80) FROM agent_prompt WHERE id=" + pinnedIds.split(",")[2] + "))"),
                    "old prompt rows are immutable");
            assertEquals(victimId, query(c, "SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1"));
            assertEquals(reportId, query(c, "SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1"));
            assertEquals("5", query(c, "SELECT COUNT(*) FROM agent_prompt WHERE is_active=1"));
            assertEquals("5", query(c, "SELECT COUNT(DISTINCT prompt_type) FROM agent_prompt WHERE is_active=1"));
            assertEquals("1", query(c, "SELECT COUNT(*) FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1 "
                    + "AND template_content LIKE '%本场固定15轮%'"));
            assertEquals("1", query(c, "SELECT COUNT(*) FROM agent_prompt WHERE prompt_type='EVENT' AND is_active=1 "
                    + "AND template_content LIKE '%V3.5按整轮提取证据%'"));
            assertEquals("1", query(c, "SELECT COUNT(*) FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1 "
                    + "AND template_content LIKE '%methodShort 是 2 至 12 字的手段标签%'"));
            // Exactly one active version per touched type. Later migrations may add a newer version (V49 adds the
            // scene-rule variable to SCAMMER), but they must never leave two active rows for one type.
            assertEquals("3", query(c, "SELECT COUNT(*) FROM agent_prompt WHERE is_active=1 AND prompt_type IN "
                    + "('SCAMMER','EVENT','NARRATOR')"));
            assertEquals("1", query(c, "SELECT COUNT(*) FROM agent_prompt WHERE is_active=1 AND prompt_type='SCAMMER' "
                    + "AND id > " + pinnedIds.split(",")[0]), "V45 superseded the pinned SCAMMER row");
        }
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
}
