package com.aiscam.antiscam.report.engine;

import com.aiscam.antiscam.agent.AgentRunner;
import com.aiscam.antiscam.agent.ReportValidationException;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.report.service.ReportContextLoader;
import com.aiscam.antiscam.report.service.ReportTransitions;
import com.aiscam.antiscam.report.service.ReportTransitions.Claimed;
import com.aiscam.antiscam.simulation.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Persistent queue discovery is independent of both browser connections and simulation execution resources. */
@Component
public class ReportExecutor {
    private static final Logger log = LoggerFactory.getLogger(ReportExecutor.class);
    private final SimulationReportRepository reports;
    private final ReportTransitions transitions;
    private final ReportContextLoader contexts;
    private final AgentRunner runner;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SimulationProperties properties;
    private final Semaphore permits;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean ready;

    public ReportExecutor(SimulationReportRepository reports, ReportTransitions transitions, ReportContextLoader contexts,
            AgentRunner runner, ObjectMapper mapper, Clock clock, SimulationProperties properties) {
        this.reports = reports;
        this.transitions = transitions;
        this.contexts = contexts;
        this.runner = runner;
        this.mapper = mapper;
        this.clock = clock;
        this.properties = properties;
        this.permits = new Semaphore(properties.getReport().getMaxConcurrent());
    }

    public void markReady() { ready = properties.getReport().isEnabled(); }

    @Scheduled(scheduler = "reportScheduler", fixedDelayString = "${app.simulation.report.poll-ms:1000}",
            initialDelayString = "${app.simulation.report.initial-delay-ms:2000}")
    public synchronized void poll() {
        if (!ready) return;
        try {
            transitions.failExpired(clock.instant());
            for (var row : reports.findTop5ByStatusOrderByQueuedAtAscSessionIdAsc(ReportStatus.PENDING)) {
                if (!permits.tryAcquire()) break;
                boolean dispatched = false;
                try {
                    var claimed = transitions.claim(row.getSessionId(), clock.instant());
                    if (claimed != null) {
                        workers.execute(() -> attempt(claimed));
                        dispatched = true;
                    }
                } finally {
                    if (!dispatched) permits.release();
                }
            }
        } catch (RuntimeException e) {
            log.warn("report poll failed category={}", e.getClass().getSimpleName());
        }
    }

    private void attempt(Claimed claimed) {
        long began = System.nanoTime();
        Future<?> call = null;
        try {
            String json;
            try {
                var context = contexts.load(claimed.sessionId());
                long remaining = ChronoUnit.MILLIS.between(clock.instant(), claimed.attemptDeadlineAt());
                if (remaining <= 0) return; // The deadline sweep owns expired rows.
                long budget = Math.min(remaining, properties.getLlm().getCallTimeoutMs());
                var result = workers.submit(() -> runner.report(context, budget));
                call = result;
                json = mapper.writeValueAsString(result.get(budget, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                saveFailure(claimed, clock.instant().isAfter(claimed.attemptDeadlineAt())
                        ? ReportErrorCode.TIMEOUT : ReportErrorCode.GENERATION_FAILED);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // Startup recovery will converge unfinished work after shutdown.
            } catch (ExecutionException e) {
                saveFailure(claimed, e.getCause() instanceof ReportValidationException
                        ? ReportErrorCode.VALIDATION_FAILED : ReportErrorCode.GENERATION_FAILED);
                return;
            } catch (DataAccessException | JsonProcessingException e) {
                saveFailure(claimed, ReportErrorCode.PERSISTENCE_FAILED);
                return;
            } catch (RuntimeException e) {
                saveFailure(claimed, e instanceof ReportValidationException
                        ? ReportErrorCode.VALIDATION_FAILED : ReportErrorCode.GENERATION_FAILED);
                return;
            }
            // Persistence errors must not cause a second write: the timeout sweep resolves ambiguous commits.
            boolean saved = transitions.markSucceeded(claimed, json, clock.instant());
            log.info("report sessionId={} attemptNo={} result={} ms={}", claimed.sessionId(), claimed.attemptNo(),
                    saved ? "SUCCEEDED" : "STALE", (System.nanoTime() - began) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("report save failed sessionId={} attemptNo={} category={}", claimed.sessionId(), claimed.attemptNo(),
                    e.getClass().getSimpleName());
        } finally {
            if (call != null && !call.isDone()) call.cancel(true);
            permits.release();
        }
    }

    private void saveFailure(Claimed claimed, ReportErrorCode code) {
        boolean saved = transitions.markFailed(claimed, code, clock.instant());
        log.info("report sessionId={} attemptNo={} category={} result={}", claimed.sessionId(), claimed.attemptNo(), code,
                saved ? "FAILED" : "STALE");
    }

    @PreDestroy
    public void close() { ready = false; workers.shutdownNow(); }
}
