package com.aiscam.antiscam.history.service;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.history.page.HistoryCursor;
import com.aiscam.antiscam.history.web.dto.HistoryViews.*;
import com.aiscam.antiscam.simulation.domain.*;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class HistoryService {
    private final SimulationSessionRepository sessions;
    private final SimulationReportRepository reports;
    private final HistoryCursor cursors;
    private final HistoryAssembler assembler;

    public HistoryService(SimulationSessionRepository sessions, SimulationReportRepository reports,
            HistoryCursor cursors, HistoryAssembler assembler) {
        this.sessions = sessions;
        this.reports = reports;
        this.cursors = cursors;
        this.assembler = assembler;
    }

    public HistoryPage list(long userId, String account, String cursor, int limit) {
        var key = cursors.decode(account, cursor);
        var page = PageRequest.of(0, limit + 1);
        var rows = key == null ? sessions.findByUserIdOrderByStartedAtDescIdDesc(userId, page)
                : sessions.findPageBefore(userId, key.startedAt(), key.id(), page);
        boolean more = rows.size() > limit;
        var visible = more ? rows.subList(0, limit) : rows;
        var finishedIds = visible.stream().filter(s -> s.getStatus() == SimulationStatus.FINISHED)
                .map(SimulationSession::getId).toList();
        var reportRows = finishedIds.isEmpty() ? Map.<Long, SimulationReport>of()
                : reports.findBySessionIdIn(finishedIds).stream()
                        .collect(Collectors.toMap(SimulationReport::getSessionId, Function.identity()));
        var items = visible.stream().map(s -> assembler.item(s, reportRows.get(s.getId()))).toList();
        var last = more ? visible.getLast() : null;
        return assembler.page(items, more ? cursors.encode(account, last.getStartedAt(), last.getId()) : null, more);
    }

    public HistoryDetail detail(long userId, String id) {
        var session = sessions.findById(PageQuery.id(id)).filter(s -> s.getUserId() == userId)
                .orElseThrow(ApiException::notFound);
        return assembler.detail(session);
    }
}
