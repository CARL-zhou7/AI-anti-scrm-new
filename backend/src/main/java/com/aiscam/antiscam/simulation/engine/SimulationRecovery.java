package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.simulation.domain.InterruptionCode;
import com.aiscam.antiscam.simulation.domain.SimulationSessionRepository;
import com.aiscam.antiscam.simulation.domain.SimulationStatus;
import com.aiscam.antiscam.simulation.service.SimulationService;
import com.aiscam.antiscam.simulation.service.SimulationTransitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * Restart convergence (DB design §7.6): before starts are accepted, every RUNNING session whose execution context
 * was lost becomes INTERRUPTED/SERVICE_RESTARTED — never a back-filled formal result — and temp audio is cleared.
 * Also converges a user's running simulation as USER_EXIT when their HTTP session ends (logout/expiry).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimulationRecovery implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SimulationRecovery.class);
    private final SimulationSessionRepository sessions;
    private final SimulationTransitions transitions;
    private final SimulationService service;
    private final TempAudioStore audio;

    public SimulationRecovery(SimulationSessionRepository sessions, SimulationTransitions transitions,
            SimulationService service, TempAudioStore audio) {
        this.sessions = sessions;
        this.transitions = transitions;
        this.service = service;
        this.audio = audio;
    }

    @Override
    public void run(ApplicationArguments args) {
        var running = sessions.findByStatus(SimulationStatus.RUNNING);
        for (var session : running) transitions.interrupt(session.getId(), InterruptionCode.SERVICE_RESTARTED);
        audio.releaseAll();
        if (!running.isEmpty()) log.info("Converged {} running simulation(s) as SERVICE_RESTARTED", running.size());
        service.markRecovered();
    }

    @EventListener
    public void onLogout(HttpSessionDestroyedEvent event) {
        try {
            for (var context : event.getSecurityContexts()) {
                if (context.getAuthentication() != null && context.getAuthentication().getPrincipal() instanceof Account account) {
                    sessions.findFirstByUserIdAndStatus(Long.parseLong(account.id()), SimulationStatus.RUNNING)
                            .ifPresent(s -> transitions.interrupt(s.getId(), InterruptionCode.USER_EXIT));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not converge simulation on logout: {}", e.getClass().getSimpleName());
        }
    }
}
