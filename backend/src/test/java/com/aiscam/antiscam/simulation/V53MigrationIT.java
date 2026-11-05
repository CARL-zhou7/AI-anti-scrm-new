package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "V14_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/v14_it.*")
class V53MigrationIT {
    @Test void seedsOnlyTheScammerRebateRuleAndPreservesExistingRules() throws Exception {
        String url = System.getenv("V14_TEST_JDBC_URL").replaceFirst("/v14_it[^?]*",
                "/v14_it_v53_" + UUID.randomUUID().toString().replace("-", ""));
        url += (url.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
        Flyway.configure().dataSource(url, "root", "").target("52").load().migrate();
        try (var c = DriverManager.getConnection(url, "root", ""); var s = c.createStatement()) {
            s.executeUpdate("INSERT INTO sys_user(id,username,password_hash,role) VALUES (501,'v53-admin','test','ADMIN')");
            s.executeUpdate("INSERT INTO scene_rule(scenario_code,target_role,title,rule_text,created_by) VALUES ('TASK_REBATE','SCAMMER','旧规则','保留正文',501)");
        }
        var flyway = Flyway.configure().dataSource(url, "root", "").target("53").load();
        flyway.migrate();
        flyway.validate();
        try (var c = DriverManager.getConnection(url, "root", ""); var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT scenario_code,target_role,status,rule_text,created_by FROM scene_rule WHERE title='小额返利实际兑现'")) {
                assertTrue(r.next());
                assertEquals("TASK_REBATE", r.getString(1));
                assertEquals("SCAMMER", r.getString(2));
                assertEquals("ENABLED", r.getString(3));
                String text = r.getString(4);
                for (String required : new String[]{"必须在本轮执行模拟转账", "fundActions", "REWARD", "REFUND", "不重复支付", "额度不足", "不替受害者决定"})
                    assertTrue(text.contains(required), required);
                assertEquals(501, r.getLong(5));
                assertFalse(r.next());
            }
            try (var r = s.executeQuery("SELECT rule_text FROM scene_rule WHERE title='旧规则'")) {
                assertTrue(r.next());
                assertEquals("保留正文", r.getString(1));
            }
        }
    }
}
