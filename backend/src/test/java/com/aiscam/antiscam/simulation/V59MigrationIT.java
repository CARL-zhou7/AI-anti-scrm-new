package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "V14_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/v14_it.*")
class V59MigrationIT {
    @Test void mergesRulesAndPreservesSourcesSnapshotsAndUnrelatedRules() throws Exception {
        for (int keptRuleCreator : new int[]{501, 0, 502}) {
            String url = System.getenv("V14_TEST_JDBC_URL").replaceFirst("/v14_it[^?]*",
                    "/v14_it_v59_" + UUID.randomUUID().toString().replace("-", ""));
            url += (url.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
            Flyway.configure().dataSource(url, "root", "").target("58").load().migrate();
            long session;
            try (var c = DriverManager.getConnection(url, "root", "")) {
                execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (501,'merge-admin','test','ADMIN')");
                if (keptRuleCreator == 502)
                    execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (502,'other-admin','test','ADMIN')");
                session = insertSession(c, 501);
                execute(c, "UPDATE simulation_session SET scene_rule_snapshot='[]' WHERE id=" + session);
                execute(c, "INSERT INTO simulation_round(id,session_id,round_no) VALUES (601," + session + ",1)");
                execute(c, "INSERT INTO simulation_scammer_draft(id,session_id,round_id,round_no,revision_no,status,original_content,draft_content,created_by) VALUES (701," + session + ",601,1,0,'ADOPTED','原始','最终',501)");
                execute(c, "INSERT INTO simulation_message(id,round_id,speaker_role,content) VALUES (751,601,'SCAMMER','最终')");
                execute(c, "INSERT INTO simulation_intervention(id,session_id,round_id,round_no,draft_id,status,mode,scene_code,admin_user_id,original_content,final_content,final_message_id) VALUES (801," + session + ",601,1,701,'LINKED','ADMIN_TEST','TASK_REBATE',501,'原始','最终',751)");
                execute(c, "INSERT INTO scene_rule(id,scenario_code,target_role,title,rule_text,created_by) VALUES (901,'TASK_REBATE','SCAMMER','小额返现即时到账验证','旧正文',501)");
                if (keptRuleCreator != 0)
                    execute(c, "INSERT INTO scene_rule(id,scenario_code,target_role,title,rule_text,created_by) VALUES (902,'TASK_REBATE','SCAMMER','小额返利实际兑现','旧兑现'," + keptRuleCreator + ")");
                execute(c, "INSERT INTO scene_rule_source(rule_id,intervention_id,admin_user_id) VALUES (901,801,501)");
                execute(c, "INSERT INTO scene_rule(scenario_code,target_role,title,rule_text,created_by) VALUES ('TASK_REBATE','VICTIM','小额返现即时到账验证','受害者规则保留',501)");
            }
            var flyway = Flyway.configure().dataSource(url, "root", "").target("61").load();
            flyway.migrate(); flyway.validate();
            try (var c = DriverManager.getConnection(url, "root", "")) {
                assertEquals("0", query(c, "SELECT COUNT(*) FROM scene_rule WHERE target_role='SCAMMER' AND title='小额返现即时到账验证'"));
                assertEquals("1", query(c, "SELECT COUNT(*) FROM scene_rule WHERE target_role='SCAMMER' AND title='小额返利实际兑现'"));
                assertEquals(keptRuleCreator == 0 ? "901" : "902", query(c, "SELECT rule_id FROM scene_rule_source WHERE intervention_id=801"));
                assertEquals("501", query(c, "SELECT admin_user_id FROM scene_rule_source WHERE intervention_id=801"));
                assertEquals("受害者规则保留", query(c, "SELECT rule_text FROM scene_rule WHERE target_role='VICTIM'"));
                assertEquals("[]", query(c, "SELECT scene_rule_snapshot FROM simulation_session WHERE id=" + session));
                String rule = query(c, "SELECT rule_text FROM scene_rule WHERE target_role='SCAMMER'");
                for (String required : new String[]{"前置条件核对", "此前已承诺", "立即执行", "明确提示查收", "虚假截图", "fundActions", "不得重复支付", "额度不足"})
                    assertTrue(rule.contains(required), required);
            }
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
