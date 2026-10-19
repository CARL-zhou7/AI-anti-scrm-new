package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.simulation.domain.SimulationSession;
import com.aiscam.antiscam.simulation.domain.SimulationSessionRepository;
import com.aiscam.antiscam.simulation.service.SimulationStateAssembler;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.ReportView;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final SimulationSessionRepository sessions;
    private final SimulationStateAssembler assembler;
    private final ReportTransitions transitions;
    private final Clock clock;

    public ReportService(SimulationSessionRepository sessions, SimulationStateAssembler assembler,
            ReportTransitions transitions, Clock clock) {
        this.sessions = sessions;
        this.assembler = assembler;
        this.transitions = transitions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportView view(long userId, String id) { return assembler.reportView(owned(userId, id)); }

    @Transactional
    public ReportRetryResult retry(long userId, String id) {
        return transitions.retry(owned(userId, id), clock.instant());
    }

    private SimulationSession owned(long userId, String id) {
        return sessions.findById(PageQuery.id(id)).filter(s -> s.getUserId() == userId).orElseThrow(ApiException::notFound);
    }
}
