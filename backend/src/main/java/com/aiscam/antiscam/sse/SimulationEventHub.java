package com.aiscam.antiscam.sse;

import com.aiscam.antiscam.config.SimulationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Per-simulation SSE fan-out. Frames carry {@code id:}, {@code event:} and a single-line JSON {@code data:} of
 * {eventId, sessionId, stateVersion, data}. Business events are only sent after the transaction that saved the
 * underlying facts has committed; there is no persistent event log and no Last-Event-ID replay.
 */
@Component
public class SimulationEventHub {
    private static final Logger log = LoggerFactory.getLogger(SimulationEventHub.class);
    private final Map<Long, List<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastDisconnectedAt = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final SimulationProperties properties;
    private final Clock clock;

    public SimulationEventHub(ObjectMapper mapper, SimulationProperties properties, Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    public record SimulationEvent(String type, long sessionId, long stateVersion, Object data) {}

    private record Subscriber(SseEmitter emitter, String httpSessionId) {}

    /** Registers a stream and immediately sends the authoritative {@code session.state} snapshot. */
    public SseEmitter subscribe(long sessionId, String httpSessionId, SimulationEvent initialState) {
        var emitter = new SseEmitter(properties.getSse().getEmitterTimeoutMs());
        var subscriber = new Subscriber(emitter, httpSessionId);
        subscribers.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(subscriber);
        Runnable remove = () -> detach(sessionId, subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(sessionId, subscriber, initialState);
        return emitter;
    }

    /** Publishes after the current transaction commits (immediately when no transaction is active). */
    public void publishAfterCommit(SimulationEvent event) {
        afterCommit(() -> publish(event));
    }

    public void publish(SimulationEvent event) {
        for (var subscriber : subscribers.getOrDefault(event.sessionId(), List.of())) send(event.sessionId(), subscriber, event);
    }

    /** Completes every stream of a simulation (after an interruption has been published). */
    public void close(long sessionId) {
        var list = subscribers.remove(sessionId);
        if (list != null) list.forEach(s -> s.emitter().complete());
        lastDisconnectedAt.remove(sessionId);
    }

    public int activeCount(long sessionId) { return subscribers.getOrDefault(sessionId, List.of()).size(); }

    public Instant lastDisconnectedAt(long sessionId) { return lastDisconnectedAt.get(sessionId); }

    /** Logout, expiry or replacement by a newer login closes that HTTP session's streams immediately. */
    @EventListener
    public void onHttpSessionDestroyed(HttpSessionDestroyedEvent event) {
        String id = event.getId();
        subscribers.forEach((sessionId, list) -> list.stream().filter(s -> s.httpSessionId().equals(id))
                .forEach(s -> { s.emitter().complete(); detach(sessionId, s); }));
    }

    @Scheduled(fixedDelayString = "${app.simulation.sse.heartbeat-ms:15000}")
    public void heartbeat() {
        subscribers.forEach((sessionId, list) -> list.forEach(s -> {
            try { s.emitter().send(SseEmitter.event().comment("ping")); }
            catch (IOException | IllegalStateException e) { detach(sessionId, s); }
        }));
    }

    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private void send(long sessionId, Subscriber subscriber, SimulationEvent event) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("sessionId", Long.toString(event.sessionId()));
        envelope.put("stateVersion", Long.toString(event.stateVersion()));
        envelope.put("data", event.data());
        try {
            subscriber.emitter().send(SseEmitter.event().id(eventId).name(event.type()).data(mapper.writeValueAsString(envelope)));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE send failed for simulation {}", sessionId);
            detach(sessionId, subscriber);
        }
    }

    private void detach(long sessionId, Subscriber subscriber) {
        var list = subscribers.get(sessionId);
        if (list != null && list.remove(subscriber)) {
            if (list.isEmpty()) lastDisconnectedAt.put(sessionId, clock.instant());
        }
    }
}
