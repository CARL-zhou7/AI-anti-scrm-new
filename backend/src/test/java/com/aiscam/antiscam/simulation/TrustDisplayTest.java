package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SimulationStateAssembler;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.TrustChange;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrustDisplayTest {
    @Test
    void displaysRoundLedgerNetChangeWithoutUsingCurrentOrFutureScores() {
        var events = mock(SimulationTrustEventRepository.class);
        var assembler = new SimulationStateAssembler(null, null, null, null, null, events);
        var session = mock(SimulationSession.class);
        when(session.getId()).thenReturn(1L);
        when(session.getInitialTrustScore()).thenReturn(65);
        var ledger = List.of(event(1, 80), event(2, 100), event(2, 93), event(4, 40));
        when(events.findBySessionIdOrderByIdAsc(1L)).thenReturn(ledger);
        assertEquals(new TrustChange(65, 80, 15), assembler.analysis(session, round(1)).trustChange());
        assertEquals(new TrustChange(80, 93, 13), assembler.analysis(session, round(2)).trustChange());
        assertEquals(new TrustChange(93, 93, 0), assembler.analysis(session, round(3)).trustChange());
        assertEquals(new TrustChange(93, 40, -53), assembler.analysis(session, round(4)).trustChange());
        when(events.findBySessionIdOrderByIdAsc(1L)).thenReturn(List.of());
        assertEquals(new TrustChange(65, 65, 0), assembler.analysis(session, round(1)).trustChange());
        when(session.getInitialTrustScore()).thenReturn(null);
        assertNull(assembler.analysis(session, round(1)).trustChange());
        assertNull(assembler.analysis(session, mock(SimulationRound.class)));
    }

    private SimulationRound round(int n) {
        var r = mock(SimulationRound.class);
        when(r.getRoundNo()).thenReturn(n);
        when(r.hasAnalysis()).thenReturn(true);
        return r;
    }

    private SimulationTrustEvent event(int n, int after) {
        var event = mock(SimulationTrustEvent.class);
        when(event.getRoundNo()).thenReturn(n);
        when(event.getScoreAfter()).thenReturn(after);
        return event;
    }
}
