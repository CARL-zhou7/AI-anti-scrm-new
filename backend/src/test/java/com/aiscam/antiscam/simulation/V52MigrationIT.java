package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Uses uniquely named schemas on the explicitly configured isolated local test server; never cleans an existing schema. */
@EnabledIfEnvironmentVariable(named = "V14_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/v14_it.*")
class V52MigrationIT {
    @Test void emptyDatabaseWithoutAdministratorMigratesWithoutInventingAnAccount() throws Exception {
        String url = isolatedUrl("empty");
        var flyway = Flyway.configure().dataSource(url, "root", "").target("52").load();
        flyway.migrate();
        flyway.validate();
        try (var c = DriverManager.getConnection(url, "root", "")) {
            assertEquals("0", query(c, "SELECT COUNT(*) FROM sys_user WHERE role='ADMIN'"));
            assertEquals("0", query(c, "SELECT COUNT(*) FROM scene_rule WHERE target_role='VICTIM'"));
            assertEquals("52", flyway.info().current().getVersion().getVersion());
        }
    }

    @Test void upgradePreservesOldRulesAndSnapshotsAndAddsRoleAndDecisionConstraints() throws Exception {
        String url = isolatedUrl("upgrade");
        Flyway.configure().dataSource(url, "root", "").target("51").load().migrate();
        long sessionId;
        String originalSnapshot;
        try (var c = DriverManager.getConnection(url, "root", "")) {
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (501,'v52-admin','test','ADMIN')");
            execute(c, "INSERT INTO scene_rule(id,scenario_code,title,rule_text,created_by) VALUES (901,'TASK_REBATE','相同标题','旧规则正文',501)");
            sessionId = insertSession(c);
            execute(c, "INSERT INTO simulation_round(id,session_id,round_no) VALUES (601," + sessionId + ",1)");
            execute(c, "UPDATE simulation_session SET scene_rule_snapshot='[{\"ruleId\":\"901\",\"title\":\"相同标题\",\"ruleText\":\"旧规则正文\",\"version\":1}]' WHERE id=" + sessionId);
            originalSnapshot = query(c, "SELECT scene_rule_snapshot FROM simulation_session WHERE id=" + sessionId);
        }
        var flyway = Flyway.configure().dataSource(url, "root", "").target("52").load();
        flyway.migrate();
        flyway.validate();
        try (var c = DriverManager.getConnection(url, "root", "")) {
            assertEquals(originalSnapshot, query(c, "SELECT scene_rule_snapshot FROM simulation_session WHERE id=" + sessionId));
            assertEquals("SCAMMER", query(c, "SELECT target_role FROM scene_rule WHERE id=901"));
            assertEquals("旧规则正文", query(c, "SELECT rule_text FROM scene_rule WHERE id=901"));
            assertEquals("3", query(c, "SELECT COUNT(*) FROM scene_rule WHERE target_role='VICTIM' AND created_by=501"));
            assertEquals("3", query(c, "SELECT COUNT(DISTINCT scenario_code) FROM scene_rule WHERE target_role='VICTIM'"));
            execute(c, "INSERT INTO scene_rule(scenario_code,target_role,title,rule_text,created_by) VALUES ('TASK_REBATE','VICTIM','相同标题','新受害者规则',501)");
            assertThrows(SQLException.class, () -> execute(c, "INSERT INTO scene_rule(scenario_code,target_role,title,rule_text,created_by) VALUES ('TASK_REBATE','VICTIM','相同标题','重复',501)"));
            assertThrows(SQLException.class, () -> execute(c, "INSERT INTO scene_rule(scenario_code,target_role,title,rule_text,created_by) VALUES ('TASK_REBATE','SCAMMER','相同标题','重复',501)"));
            assertThrows(SQLException.class, () -> execute(c, "UPDATE scene_rule SET target_role='REPORT' WHERE id=901"));
            assertEquals("1", query(c, "SELECT victim_guidance_json IS NULL FROM simulation_round WHERE id=601"));
            execute(c, "UPDATE simulation_round SET victim_guidance_json='{}' WHERE id=601");
            execute(c, "UPDATE simulation_round SET victim_guidance_json='{\"roundNo\":1,\"required\":false}' WHERE id=601");
            assertThrows(SQLException.class, () -> execute(c, "UPDATE simulation_round SET victim_guidance_json='[]' WHERE id=601"));
            assertThrows(SQLException.class, () -> execute(c, "UPDATE simulation_round SET victim_guidance_json='1' WHERE id=601"));
            execute(c, "UPDATE simulation_round SET victim_guidance_json=NULL WHERE id=601");
        }
    }

    private static String isolatedUrl(String name) {
        String schema = "v14_it_v52_" + name + "_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("V14_TEST_JDBC_URL").replaceFirst("/v14_it[^?]*", "/" + schema);
        return url + (url.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
    }

    private static long insertSession(Connection c) throws SQLException {
        String requestId = UUID.randomUUID().toString();
        execute(c, """
            INSERT INTO simulation_session(user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
              snapshot_schema_version,scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,
              narrator_prompt_id,report_prompt_id,event_prompt_id,runtime_snapshot,status,current_round_no,started_at,
              received_at,timing_evidence,intervention_mode)
            SELECT 501,'%s',1,1,1,4,'{}','{}','{}',
              (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
              (SELECT id FROM agent_prompt WHERE prompt_type='EVENT' AND is_active=1),
              '{"ruleset_version":"PRD-V3.5","trust_config":{"trust_ruleset_version":"trust-v3"}}',
              'RUNNING',0,NOW(3),NOW(3),'{}','ADMIN_TEST'
            """.formatted(requestId));
        return Long.parseLong(query(c, "SELECT id FROM simulation_session WHERE start_request_id='" + requestId + "'"));
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) { s.execute(sql); }
    }
    private static String query(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }
}
