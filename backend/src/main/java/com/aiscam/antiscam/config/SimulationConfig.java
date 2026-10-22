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
        if (!tts.equals("qwen") && !tts.equals("silent")) throw new IllegalStateException("Unknown TTS provider " + tts);
        if (!devOrTest && (llm.equals("scripted") || tts.equals("silent"))) {
            throw new IllegalStateException("scripted/silent simulation providers are only allowed in dev or test profiles");
        }
        if (tts.equals("qwen")) {
            var qwen = properties.getTts();
            if (qwen.getQwenApiKey().isBlank() || qwen.getQwenBaseUrl().isBlank()) throw new IllegalStateException("QWEN_API_KEY and QWEN_TTS_BASE_URL must be set");
            if (!"beijing".equals(qwen.getQwenRegion())) throw new IllegalStateException("Qwen TTS requires the Beijing region");
            if (!"qwen-audio-3.1-tts-flash".equals(qwen.getQwenModel())) throw new IllegalStateException("Unsupported Qwen TTS model");
            if (!devOrTest && !qwen.getQwenBaseUrl().matches("https://[a-zA-Z0-9-]+\\.cn-beijing\\.maas\\.aliyuncs\\.com/?")) {
                throw new IllegalStateException("Qwen TTS requires a Beijing workspace HTTPS endpoint");
            }
        }
        if (!devOrTest && llm.equals("deepseek") && properties.getLlm().getApiKey().isBlank()) {
            throw new IllegalStateException("LLM_API_KEY must be set outside dev/test");
        }
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean(name = {"simulationScheduler", "taskScheduler"}, destroyMethod = "shutdown")
    ThreadPoolTaskScheduler simulationScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sim-timer-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler reportScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("report-timer-");
        scheduler.initialize();
        return scheduler;
    }
}
