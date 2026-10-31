package com.aiscam.antiscam.simulation.web;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.simulation.service.InterventionService;
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.*;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.State;
import java.util.Map;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/**
 * Round review (API-47～49), available to both login roles during private testing.
 * The service only serves the caller's own sessions with the review gate enabled.
 */
@RestController
@RequestMapping({"/anti.scam/v1/simulations/{id}/intervention", "/anti.scam/v1/admin/simulations/{id}/intervention"})
public class InterventionController {
    private final InterventionService service;

    public InterventionController(InterventionService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<InterventionState>> state(@PathVariable String id,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return noStore(service.state(userId(auth), sessionId(id)));
    }

    /** API-48: continue with the current draft or an edited one. */
    @PostMapping("/adopt")
    public ResponseEntity<ApiResponse<State>> adopt(@PathVariable String id, @RequestBody AdoptRequest body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return noStore(service.adopt(userId(auth), sessionId(id), body));
    }

    /** API-49: regenerate the round's draft; the previous draft is discarded, never adopted. */
    @PostMapping("/regenerate")
    public ResponseEntity<ApiResponse<InterventionState>> regenerate(@PathVariable String id,
            @RequestBody DraftRequest body, @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return noStore(service.regenerate(userId(auth), sessionId(id), body));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<InterventionState>> revise(@PathVariable String id, @RequestBody DraftRequest body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return noStore(service.revise(userId(auth), sessionId(id), body));
    }

    private static long sessionId(String id) {
        try { return Long.parseLong(id); } catch (NumberFormatException e) { throw com.aiscam.antiscam.common.error.ApiException.notFound(); }
    }

    private static long userId(Authentication auth) { return Long.parseLong(((Account) auth.getPrincipal()).id()); }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(data));
    }
}
