package com.aiscam.antiscam.auth;

import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.engine.SimulationRecovery;
import com.aiscam.antiscam.simulation.service.*;
import com.aiscam.antiscam.sse.SimulationEventHub;
import com.aiscam.antiscam.timing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.session.SessionIdChangedEvent;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginLifecycleTest {
    @Test void loginRotationPreservesUserTimingAndAllowsStreamsToCloseOnExpiry() {
        var now = Instant.parse("2026-10-27T00:00:00Z");
        var properties = new SimulationProperties();
        var samples = new TimingSampleStore(properties, Clock.fixed(now, ZoneOffset.UTC));
        var sample = samples.issue("old",7);
        var unrelated = samples.issue("another-browser",8);
        var hub = new SimulationEventHub(new ObjectMapper(),properties,Clock.fixed(now,ZoneOffset.UTC));
        hub.subscribe(1,"old",new SimulationEventHub.SimulationEvent("session.state",1,1,java.util.Map.of()));
        hub.subscribe(2,"another-browser",new SimulationEventHub.SimulationEvent("session.state",2,1,java.util.Map.of()));
        var changed = mock(SessionIdChangedEvent.class);
        when(changed.getOldSessionId()).thenReturn("old");
        when(changed.getNewSessionId()).thenReturn("new");
        samples.onSessionIdChanged(changed);
        hub.onSessionIdChanged(changed);
        assertEquals(unrelated,samples.find(unrelated.sampleId()).orElseThrow());
        var timing = new TimingCalibrator.TimingEvidence("1.0",sample.sampleId(),1000.0,1200.0,4100.0);
        assertEquals(now.plusMillis(3000),new TimingCalibrator(samples,properties)
                .calibrate(timing,"new",7,now.plusMillis(3500)).startedAt());
        assertEquals(1,hub.activeCount(1));
        hub.onHttpSessionDestroyed(new HttpSessionDestroyedEvent(new MockHttpSession(null,"new")));
        assertEquals(0,hub.activeCount(1));
        assertEquals(1,hub.activeCount(2));
    }

    @Test void onlyUserLogoutInterruptsTheirSimulationAndFailuresDoNotBlockLogout() {
        var sessions = mock(SimulationSessionRepository.class);
        var transitions = mock(SimulationTransitions.class);
        var recovery = new SimulationRecovery(sessions,transitions,mock(SimulationService.class),mock(TempAudioStore.class));
        var admin = UsernamePasswordAuthenticationToken.authenticated(new Account("102","admin",UserRole.ADMIN),null,List.of());
        recovery.onScopedLogout(new LogoutSuccessEvent(admin));
        verifyNoInteractions(sessions,transitions);
        var running = mock(SimulationSession.class);
        when(running.getId()).thenReturn(12L);
        when(sessions.findFirstByUserIdAndStatus(101,SimulationStatus.RUNNING)).thenReturn(Optional.of(running));
        var user = UsernamePasswordAuthenticationToken.authenticated(new Account("101","用户",UserRole.USER),null,List.of());
        recovery.onScopedLogout(new LogoutSuccessEvent(user));
        verify(transitions).interrupt(12,InterruptionCode.USER_EXIT);
        when(sessions.findFirstByUserIdAndStatus(101,SimulationStatus.RUNNING)).thenThrow(new IllegalStateException());
        assertDoesNotThrow(() -> recovery.onScopedLogout(new LogoutSuccessEvent(user)));
    }
}
