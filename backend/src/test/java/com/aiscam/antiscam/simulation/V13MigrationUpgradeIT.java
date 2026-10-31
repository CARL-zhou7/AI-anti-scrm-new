package com.aiscam.antiscam.simulation;

import java.sql.Connection;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class V13MigrationUpgradeIT {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_bin");

    @Test
    void upgradesV16WithoutRewritingHistoricalFacts() throws Exception {
        Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .target("16").load().migrate();
        try (Connection c = mysql.createConnection("")) {
            execute(c, "INSERT INTO sys_user(id,username,password_hash,role) VALUES (101,'legacy-test','test-hash','USER')");
            for (int id : new int[]{101, 102}) {
                execute(c, """
                    INSERT INTO simulation_session(id,user_id,start_request_id,scenario_id,scammer_profile_id,victim_profile_id,
                      scenario_snapshot,scammer_snapshot,victim_snapshot,scammer_prompt_id,victim_prompt_id,narrator_prompt_id,report_prompt_id,
                      runtime_snapshot,status,current_round_no,started_at,received_at,timing_evidence,deadline_at,ended_at,
                      outcome_code,outcome_reason,interruption_code,interruption_detail,
                      initial_available_funds_yuan,available_balance_yuan,total_paid_yuan,actual_loss_yuan,reserve_released_yuan)
                    SELECT %d,101,UUID(),(SELECT MIN(id) FROM fraud_scenario),(SELECT MIN(id) FROM scammer_profile),
                      (SELECT MIN(id) FROM victim_profile),'{}','{}','{"reserved_funds_yuan":9000}',
                      (SELECT id FROM agent_prompt WHERE prompt_type='SCAMMER' AND is_active=1),
                      (SELECT id FROM agent_prompt WHERE prompt_type='VICTIM' AND is_active=1),
                      (SELECT id FROM agent_prompt WHERE prompt_type='NARRATOR' AND is_active=1),
                      (SELECT id FROM agent_prompt WHERE prompt_type='REPORT' AND is_active=1),
                      '{"ruleset_version":"PRD-V3.3","fund_ruleset_version":"funds-v2"}',
                      '%s',5,'2026-10-24 00:00:00','2026-10-24 00:00:00','{}','2026-10-24 00:10:00','2026-10-24 00:01:00',
                      %s,%s,%s,%s,3000,3000,100,100,100
                    """.formatted(id, id == 101 ? "FINISHED" : "INTERRUPTED",
                        id == 101 ? "'DEFRAUDED'" : "NULL", id == 101 ? "'原结论'" : "NULL",
                        id == 102 ? "'USER_EXIT'" : "NULL", id == 102 ? "'主动退出'" : "NULL"));
            }
            execute(c, "INSERT INTO simulation_round(id,session_id,round_no) VALUES (101,101,1)");
            execute(c, """
                INSERT INTO simulation_fund_flow(session_id,round_id,sequence_no,flow_type,purpose,amount_yuan,balance_after_yuan,reason,occurred_at)
                VALUES (101,101,1,'PAYMENT','INITIAL_PAYMENT',100,2900,NULL,NOW(3)),
                       (101,101,2,'RESERVE_RELEASE','RESERVE_RELEASE',100,3000,'原预留释放',NOW(3))
                """);
            execute(c, """
                INSERT INTO simulation_report(session_id,status,attempt_no,task_token,queued_at,started_at,finished_at,content_json)
                VALUES (101,'SUCCEEDED',1,UUID(),NOW(3),NOW(3),NOW(3),'{"contentSchemaVersion":2,"summary":"旧报告原文"}')
                """);
            String snapshots = query(c, "SELECT CONCAT(victim_snapshot,runtime_snapshot) FROM simulation_session WHERE id=101");
            String report = query(c, "SELECT content_json FROM simulation_report WHERE session_id=101");
            var upgraded = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).load();
            upgraded.migrate();
            assertEquals("41", upgraded.info().current().getVersion().getVersion());
            assertEquals(snapshots, query(c, "SELECT CONCAT(victim_snapshot,runtime_snapshot) FROM simulation_session WHERE id=101"));
            assertEquals(report, query(c, "SELECT content_json FROM simulation_report WHERE session_id=101"));
            assertEquals("2,100,0,100,0,100", query(c, """
                SELECT CONCAT_WS(',',snapshot_schema_version,reserve_released_yuan,emergency_reserve_released_yuan,
                  general_reserve_released_yuan,total_rewarded_yuan,actual_loss_yuan) FROM simulation_session WHERE id=101
                """));
            assertNull(query(c, "SELECT initial_trust_score FROM simulation_session WHERE id=101"));
            assertEquals("0", query(c, "SELECT COUNT(*) FROM simulation_report WHERE session_id=102"));
            assertEquals("40,70,85", query(c, "SELECT CONCAT_WS(',',trust_threshold_accept,trust_threshold_small,trust_threshold_large) FROM simulation_policy WHERE id=1"));
            assertEquals("7500,1500,9000", query(c, "SELECT CONCAT_WS(',',general_reserve_funds_yuan,emergency_reserve_funds_yuan,reserved_funds_yuan) FROM victim_profile WHERE name='林晓'"));
            assertEquals("9", query(c, """
                SELECT COUNT(*) FROM victim_scenario_profile s JOIN victim_profile p ON p.id=s.victim_profile_id
                WHERE p.name IN ('林晓','赵诚','周国良') AND s.initial_trust_score IS NOT NULL
                AND s.positive_factor IS NOT NULL AND s.negative_factor IS NOT NULL AND s.small_amount_cap_yuan IS NOT NULL
                """));
            assertEquals("GENERAL", query(c, "SELECT reserve_tier FROM simulation_fund_flow WHERE session_id=101 AND sequence_no=2"));
            assertEquals("0", query(c, "SELECT COUNT(*) FROM simulation_trust_event"));
            assertEquals("0", query(c, "SELECT COUNT(*) FROM simulation_waiting_item"));
            upgraded.validate();
        }
    }

    private static void execute(Connection c, String sql) throws Exception {
        try (var statement = c.createStatement()) { statement.execute(sql); }
    }

    private static String query(Connection c, String sql) throws Exception {
        try (var statement = c.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
