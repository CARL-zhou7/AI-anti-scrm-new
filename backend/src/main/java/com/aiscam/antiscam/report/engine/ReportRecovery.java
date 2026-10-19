package com.aiscam.antiscam.report.engine;

import com.aiscam.antiscam.report.service.ReportTransitions;
import com.aiscam.antiscam.simulation.domain.SimulationSessionRepository;
import com.aiscam.antiscam.simulation.domain.SimulationStatus;
import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ReportRecovery implements ApplicationRunner {
    private final ReportTransitions transitions;
    private final SimulationSessionRepository sessions;
    private final ReportExecutor executor;
    private final Clock clock;

    public ReportRecovery(ReportTransitions transitions, SimulationSessionRepository sessions, ReportExecutor executor, Clock clock) {
        this.transitions = transitions;
        this.sessions = sessions;
        this.executor = executor;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        transitions.failRunningOnRestart(clock.instant());
        for (var session : sessions.findByStatus(SimulationStatus.FINISHED)) {
            transitions.repairMissing(session.getId(), clock.instant());
        }
        executor.markReady();
    }
}
