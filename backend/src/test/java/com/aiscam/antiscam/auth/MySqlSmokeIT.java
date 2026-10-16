package com.aiscam.antiscam.auth;

import com.aiscam.antiscam.auth.domain.UserRepository;
import com.aiscam.antiscam.auth.service.AuthService;
import com.aiscam.antiscam.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "MYSQL_APP_PASSWORD=test-only")
@Testcontainers
class MySqlSmokeIT {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.45")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_bin")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", mysql::getJdbcUrl);
        properties.add("spring.datasource.username", mysql::getUsername);
        properties.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void realMigrationAndGlobalCaseInsensitiveUniqueKey() {
        auth.register("ZhangSan", "😀".repeat(24), "😀".repeat(24));
        assertEquals("ZhangSan", auth.authenticate("ZHANGSAN", "😀".repeat(24)).username());
        assertEquals("USERNAME_TAKEN", assertThrows(ApiException.class,
                () -> auth.register("zhangsan", "123456", "123456")).code);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO sys_user(username,password_hash,role) VALUES ('ZHANGSAN','test-hash','ADMIN')"));
        assertEquals(1, users.count());
    }
}
