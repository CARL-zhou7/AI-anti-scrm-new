package com.aiscam.antiscam.simulation;

import static org.mockito.Mockito.*;

import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.simulation.domain.InterventionMode;
import com.aiscam.antiscam.simulation.service.SimulationService;
import com.aiscam.antiscam.simulation.service.SimulationTransitions;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.StartRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SimulationReviewStartTest {
    @Test
    void bothLoginRolesStartWithRoundReviewDuringPrivateTesting() {
        var transitions = mock(SimulationTransitions.class);
        var now = Instant.parse("2026-10-29T00:00:00Z");
        var service = new SimulationService(transitions, null, null, null, null, null, Clock.fixed(now, ZoneOffset.UTC));
        service.markRecovered();
        var request = new StartRequest("00000000-0000-4000-8000-000000000001", "1", "2", "3", null);
        for (var role : UserRole.values()) service.start(7, role, "http", request);
        verify(transitions, times(UserRole.values().length)).start(7, "http", request, 1, 2, 3, now, InterventionMode.ADMIN_TEST);
    }
}
