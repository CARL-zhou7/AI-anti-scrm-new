package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.simulation.domain.PresentationStatus;
import com.aiscam.antiscam.simulation.domain.SimulationMessageRepository;
import com.aiscam.antiscam.simulation.domain.SimulationRoundRepository;
import com.aiscam.antiscam.simulation.domain.SimulationSession;
import com.aiscam.antiscam.simulation.domain.SimulationSessionRepository;
import com.aiscam.antiscam.simulation.domain.SimulationStatus;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.*;
import com.aiscam.antiscam.media.TempAudioStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Request-level rules for the simulation API: format validation, recovery gate, ownership-scoped reads, and the
 * receipt/pause/exit entry points (which delegate every write to {@link SimulationTransitions}).
 */
@Service
public class SimulationService {
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Set<String> OPENING_ERRORS = Set.of("TEXT_RENDER_FAILED", "AVATAR_LOAD_FAILED", "ANIMATION_FAILED");
    private static final Set<String> MESSAGE_ERRORS = Set.of("TEXT_RENDER_FAILED", "ANIMATION_FAILED", "AUDIO_LOAD_FAILED", "AUDIO_PLAYBACK_FAILED");
    private static final Set<String> ANALYSIS_ERRORS = Set.of("TEXT_RENDER_FAILED");
    private static final Set<String> EXIT_REASONS = Set.of("NAVIGATION", "REFRESH", "CLOSE", "LOGOUT");

    private final SimulationTransitions transitions;
    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final SimulationStateAssembler assembler;
    private final TempAudioStore audio;
    private final Clock clock;
    private volatile boolean recovered;

    public SimulationService(SimulationTransitions transitions, SimulationSessionRepository sessions,
            SimulationRoundRepository rounds, SimulationMessageRepository messages, SimulationStateAssembler assembler,
            TempAudioStore audio, Clock clock) {
        this.transitions = transitions;
        this.sessions = sessions;
        this.rounds = rounds;
        this.messages = messages;
        this.assembler = assembler;
        this.audio = audio;
        this.clock = clock;
    }

    /** Called by startup recovery once orphaned RUNNING sessions have been converged. */
    public void markRecovered() { recovered = true; }

    public SimulationTransitions.StartResult start(long userId, String httpSessionId, StartRequest req) {
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!recovered) throw ApiException.serviceUnavailable();
        if (req.startRequestId() == null) throw ApiException.field("startRequestId", "REQUIRED", "此项不能为空");
        if (!UUID.matcher(req.startRequestId()).matches()) throw ApiException.field("startRequestId", "VALUE_INVALID", "须为UUID");
        long scenario = id("scenarioId", req.scenarioId());
        long scammer = id("scammerProfileId", req.scammerProfileId());
        long victim = id("victimProfileId", req.victimProfileId());
        if (req.timing() == null) throw ApiException.field("timing", "REQUIRED", "缺少计时证据");
        String requestId = req.startRequestId().toLowerCase();
        var normalized = new StartRequest(requestId, req.scenarioId(), req.scammerProfileId(), req.victimProfileId(), req.timing());
        try {
            return transitions.start(userId, httpSessionId, normalized, scenario, scammer, victim, receivedAt);
        } catch (DataIntegrityViolationException race) {
            // A concurrent double click won the unique keys: report the committed request or the active session.
            var same = sessions.findByUserIdAndStartRequestId(userId, requestId);
            if (same.isPresent()) return transitions.start(userId, httpSessionId, normalized, scenario, scammer, victim, receivedAt);
            var active = sessions.findFirstByUserIdAndStatus(userId, SimulationStatus.RUNNING);
            if (active.isPresent()) throw ApiException.activeSimulationExists(active.get().getId().toString());
            throw race;
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public State state(long userId, String id) {
        return assembler.assemble(owned(userId, id));
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public SessionStateData sessionState(long userId, long sessionId) {
        return assembler.sessionState(owned(userId, Long.toString(sessionId)));
    }

    /** Ownership check before an SSE stream is opened; throws 404 for foreign or unknown sessions. */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public long ownedId(long userId, String id) { return owned(userId, id).getId(); }

    public EffectResult presentOpening(long userId, String id, PresentationReceipt receipt) {
        validateReceipt(receipt, OPENING_ERRORS);
        return transitions.receiptOpening(userId, PageQuery.id(id), receipt);
    }

    public EffectResult presentMessage(long userId, String id, String messageId, PresentationReceipt receipt) {
        validateReceipt(receipt, MESSAGE_ERRORS);
        long session = PageQuery.id(id);
        long message = idParam("messageId", messageId);
        return transitions.receiptMessage(userId, session, message, receipt);
    }

    public EffectResult presentAnalysis(long userId, String id, String roundNo, PresentationReceipt receipt) {
        validateReceipt(receipt, ANALYSIS_ERRORS);
        long session = PageQuery.id(id);
        if (roundNo == null || !roundNo.matches("[1-9][0-9]?") || Integer.parseInt(roundNo) > 20) {
            throw ApiException.field("roundNo", "VALUE_INVALID", "轮号须为1至20");
        }
        return transitions.receiptAnalysis(userId, session, Integer.parseInt(roundNo), receipt);
    }

    public EffectResult pause(long userId, String id) { return transitions.pause(userId, PageQuery.id(id)); }

    public EffectResult resume(long userId, String id) { return transitions.resume(userId, PageQuery.id(id)); }

    public State exit(long userId, String id, ExitRequest request) {
        if (request == null || request.reason() == null) throw ApiException.field("reason", "REQUIRED", "此项不能为空");
        if (!EXIT_REASONS.contains(request.reason())) throw ApiException.field("reason", "VALUE_INVALID", "离开原因不受支持");
        return transitions.exit(userId, PageQuery.id(id));
    }

    /** API-21 decision order: format → ownership (404) → terminal/non-current (410) → PENDING (409) → file (200/410). */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public byte[] audio(long userId, String id, String messageId) {
        long sessionId = PageQuery.id(id);
        long mid = idParam("messageId", messageId);
        var message = messages.findById(mid).orElseThrow(ApiException::notFound);
        var round = rounds.findById(message.getRoundId()).orElseThrow(ApiException::notFound);
        var session = sessions.findById(round.getSessionId()).orElseThrow(ApiException::notFound);
        if (session.getId() != sessionId || session.getUserId() != userId) throw ApiException.notFound();
        if (!session.isRunning() || round.getRoundNo() != session.getCurrentRoundNo()) throw ApiException.audioExpired();
        if (message.getPresentationStatus() == PresentationStatus.PENDING) throw ApiException.audioNotReady();
        if (message.getPresentationStatus() != PresentationStatus.PRESENTING) throw ApiException.audioExpired();
        return audio.audio(sessionId, mid).orElseThrow(ApiException::audioExpired);
    }

    private SimulationSession owned(long userId, String id) {
        var s = sessions.findById(PageQuery.id(id)).orElseThrow(ApiException::notFound);
        if (s.getUserId() != userId) throw ApiException.notFound();
        return s;
    }

    private static void validateReceipt(PresentationReceipt receipt, Set<String> allowedErrors) {
        if (receipt == null || receipt.status() == null) throw ApiException.field("status", "REQUIRED", "此项不能为空");
        switch (receipt.status()) {
            case "COMPLETED" -> {
                if (receipt.errorCode() != null) throw ApiException.field("errorCode", "VALUE_INVALID", "完成回执不能携带错误码");
            }
            case "FAILED" -> {
                if (receipt.errorCode() == null) throw ApiException.field("errorCode", "REQUIRED", "失败回执必须携带错误码");
                if (!allowedErrors.contains(receipt.errorCode())) throw ApiException.field("errorCode", "VALUE_INVALID", "该回执不接受此错误码");
            }
            default -> throw ApiException.field("status", "VALUE_INVALID", "呈现结果只能是COMPLETED或FAILED");
        }
    }

    private static long id(String field, String value) {
        if (value == null) throw ApiException.field(field, "REQUIRED", "此项不能为空");
        return idParam(field, value);
    }

    private static long idParam(String field, String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,19}")) throw ApiException.field(field, "VALUE_INVALID", "资源标识格式不正确");
        try { return Long.parseLong(value); } catch (NumberFormatException e) { throw ApiException.notFound(); }
    }
}
