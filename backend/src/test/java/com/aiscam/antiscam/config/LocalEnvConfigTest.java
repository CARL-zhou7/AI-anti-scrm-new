package com.aiscam.antiscam.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalEnvConfigTest {
    @TempDir Path directory;

    @Test
    void envPropertiesBindDatabaseCredentialsWithoutStartingDatabase() throws Exception {
        Path env = directory.resolve(".env");
        Files.writeString(env, """
                DB_URL=jdbc:mysql://localhost:3306/custom_schema
                DB_USERNAME=local_test_user
                DB_PASSWORD=test-only#password
                """);
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=classpath:/application.yml",
                        "spring.config.import=" + env.toUri() + "[.properties]")
                .run(context -> {
                    var environment = context.getEnvironment();
                    assertEquals("jdbc:mysql://localhost:3306/custom_schema",
                            environment.getProperty("spring.datasource.url"));
                    assertEquals("local_test_user", environment.getProperty("spring.datasource.username"));
                    assertEquals("test-only#password", environment.getProperty("spring.datasource.password"));
                });
    }
}
