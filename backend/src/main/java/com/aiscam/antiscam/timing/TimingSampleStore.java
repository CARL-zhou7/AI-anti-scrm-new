package com.aiscam.antiscam.timing;

import com.aiscam.antiscam.config.SimulationProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** In-memory click-timing samples bound to one HTTP session and user; a restart invalidates all of them. */
@Component
public class TimingSampleStore {
    public static final String METHOD_VERSION = "1.0";
    private final SecureRandom random = new SecureRandom();
    private final Map<String, TimingSample> samples = new ConcurrentHashMap<>();
    private final SimulationProperties properties;
    private final Clock clock;

    public TimingSampleStore(SimulationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public TimingSample issue(String httpSessionId, long userId) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Millisecond precision so started_at (DATETIME(3)) is stored exactly as it was returned.
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var sample = new TimingSample(id, httpSessionId, userId, now,
                now.plusMillis(properties.getTiming().getSampleTtlMs()), METHOD_VERSION);
        samples.put(id, sample);
        return sample;
    }

    public Optional<TimingSample> find(String sampleId) { return Optional.ofNullable(samples.get(sampleId)); }

    @Scheduled(fixedDelay = 60_000)
    void purge() {
        Instant now = clock.instant();
        samples.values().removeIf(s -> !s.expiresAt().isAfter(now));
    }

    public record TimingSample(String sampleId, String httpSessionId, long userId, Instant serverSampledAt,
            Instant expiresAt, String methodVersion) {}
}
