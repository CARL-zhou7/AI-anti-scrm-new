package com.aiscam.antiscam.simulation.web;

import static com.aiscam.antiscam.common.web.RequestContent.rejectBody;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.simulation.engine.SimulationOrchestrator;
import com.aiscam.antiscam.simulation.service.SimulationService;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.*;
import com.aiscam.antiscam.sse.SimulationEventHub;
import com.aiscam.antiscam.sse.SimulationEventHub.SimulationEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/anti.scam/v1/simulations")
public class SimulationController {
    private final SimulationService service;
    private final SimulationEventHub hub;
    private final SimulationOrchestrator orchestrator;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public SimulationController(SimulationService service, SimulationEventHub hub, SimulationOrchestrator orchestrator,
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.service = service;
        this.hub = hub;
        this.orchestrator = orchestrator;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<State>> start(@RequestBody StartRequest body, @RequestParam MultiValueMap<String, String> query,
            Authentication auth, HttpServletRequest request) {
        PageQuery.validate(query, Set.of());
        var result = service.start(userId(auth), request.getSession().getId(), body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .header("Cache-Control", "no-store").body(ApiResponse.ok(result.state()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<State>> state(@PathVariable String id, @RequestParam MultiValueMap<String, String> query,
            Authentication auth) {
        PageQuery.validate(query, Set.of());
        return noStore(service.state(userId(auth), id));
    }

    /** Ownership is verified before any response header is written; the first frame is {@code session.state}. */
    @GetMapping("/{id}/events")
    public SseEmitter events(@PathVariable String id, @RequestParam MultiValueMap<String, String> query, Authentication auth,
            HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        long user = userId(auth);
        long sessionId;
        SessionStateData snapshot;
        try {
            PageQuery.validate(query, Set.of());
            sessionId = service.ownedId(user, id);
            snapshot = service.sessionState(user, sessionId);
        } catch (com.aiscam.antiscam.common.error.ApiException e) {
            // EventSource only accepts text/event-stream, so the JSON error body is written directly.
            response.setStatus(e.status);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            mapper.writeValue(response.getOutputStream(), ApiResponse.error(e.code, e.getMessage(), e.detail));
            return null;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        orchestrator.touch(sessionId);
        return hub.subscribe(sessionId, request.getSession().getId(), new SimulationEvent("session.state", sessionId,
                Long.parseLong(snapshot.state().stateVersion()), snapshot));
    }

    @PostMapping("/{id}/opening/presented")
    public ResponseEntity<ApiResponse<EffectResult>> opening(@PathVariable String id, @RequestBody PresentationReceipt body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return touched(id, service.presentOpening(userId(auth), id, body));
    }

    @PostMapping("/{id}/messages/{messageId}/presentation")
    public ResponseEntity<ApiResponse<EffectResult>> message(@PathVariable String id, @PathVariable String messageId,
            @RequestBody PresentationReceipt body, @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return touched(id, service.presentMessage(userId(auth), id, messageId, body));
    }

    @PostMapping("/{id}/rounds/{roundNo}/analysis/presented")
    public ResponseEntity<ApiResponse<EffectResult>> analysis(@PathVariable String id, @PathVariable String roundNo,
            @RequestBody PresentationReceipt body, @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return touched(id, service.presentAnalysis(userId(auth), id, roundNo, body));
    }

    @PostMapping("/{id}/exit")
    public ResponseEntity<ApiResponse<State>> exit(@PathVariable String id, @RequestBody ExitRequest body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        var state = service.exit(userId(auth), id, body);
        orchestrator.touch(Long.parseLong(state.sessionId()));
        return noStore(state);
    }

    /** API-43/44 take no request body; any body is rejected. */
    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<EffectResult>> pause(@PathVariable String id, @RequestBody(required = false) String body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        rejectBody(body);
        return touched(id, service.pause(userId(auth), id));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<EffectResult>> resume(@PathVariable String id, @RequestBody(required = false) String body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        rejectBody(body);
        return touched(id, service.resume(userId(auth), id));
    }

    @GetMapping("/{id}/messages/{messageId}/audio")
    public ResponseEntity<byte[]> audio(@PathVariable String id, @PathVariable String messageId,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        byte[] bytes = service.audio(userId(auth), id, messageId);
        return ResponseEntity.ok().header("Content-Type", "audio/mpeg").header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff").body(bytes);
    }

    private ResponseEntity<ApiResponse<EffectResult>> touched(String id, EffectResult result) {
        orchestrator.touch(Long.parseLong(result.state().sessionId()));
        return noStore(result);
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(body));
    }

    private static long userId(Authentication auth) { return Long.parseLong(((Account) auth.getPrincipal()).id()); }
}
