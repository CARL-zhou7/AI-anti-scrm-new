package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.config.SimulationProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Single-threaded lanes chosen by {@code sessionId % lanes}: every orchestration decision and DB transition of one
 * simulation runs serially, while different simulations proceed in parallel. Model/TTS calls never run here.
 */
@Component
public class SessionLanes {
    private static final Logger log = LoggerFactory.getLogger(SessionLanes.class);
    private final ExecutorService[] lanes;

    public SessionLanes(SimulationProperties properties) {
        int count = Math.max(1, properties.getExecutors().getLanes());
        lanes = new ExecutorService[count];
        for (int i = 0; i < count; i++) {
            int index = i;
            lanes[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sim-lane-" + index);
                t.setDaemon(true);
                return t;
            });
        }
    }

    public void run(long sessionId, Runnable task) {
        lanes[(int) Math.floorMod(sessionId, (long) lanes.length)].execute(() -> {
            try { task.run(); }
            catch (RuntimeException e) { log.error("Simulation {} lane task failed: {}", sessionId, e.getClass().getSimpleName(), e); }
        });
    }

    @PreDestroy
    void shutdown() { for (var lane : lanes) lane.shutdownNow(); }
}
