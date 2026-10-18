package com.aiscam.antiscam.config;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SimulationProperties.class)
public class SimulationConfig {
    private final SimulationProperties properties;
    private final Environment environment;

    public SimulationConfig(SimulationProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /** Production guard: the scripted model and silent synthesis exist only for dev/test and isolated smoke runs. */
    @PostConstruct
    void guard() {
        boolean devOrTest = environment.acceptsProfiles(Profiles.of("dev", "test"));
        String llm = properties.getLlm().getProvider();
        String tts = properties.getTts().getProvider();
        if (!llm.equals("deepseek") && !llm.equals("scripted")) throw new IllegalStateException("Unknown LLM provider " + llm);
        if (!tts.equals("cosyvoice") && !tts.equals("silent")) throw new IllegalStateException("Unknown TTS provider " + tts);
        if (!devOrTest && (llm.equals("scripted") || tts.equals("silent"))) {
            throw new IllegalStateException("scripted/silent simulation providers are only allowed in dev or test profiles");
        }
        if (!devOrTest && llm.equals("deepseek") && properties.getLlm().getApiKey().isBlank()) {
            throw new IllegalStateException("LLM_API_KEY must be set outside dev/test");
        }
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler simulationScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sim-timer-");
        scheduler.initialize();
        return scheduler;
    }
}
