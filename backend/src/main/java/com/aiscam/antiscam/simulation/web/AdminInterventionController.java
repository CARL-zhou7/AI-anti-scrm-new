package com.aiscam.antiscam.simulation.web;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.simulation.service.SceneRuleService;
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.InterventionRecord;
import com.aiscam.antiscam.simulation.web.dto.SceneRuleViews.RuleDraft;
import java.util.List;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/**
 * Waiting-for-knowledge-base list and induction (interface spec API-50/51). Only the caller's own {@code ADOPTED}
 * interventions are ever visible, so another administrator's sources can never be listed or merged.
 */
@RestController
@RequestMapping("/anti.scam/v1/admin/interventions")
public class AdminInterventionController {
    private final SceneRuleService service;

    public AdminInterventionController(SceneRuleService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InterventionRecord>>> list(
            @RequestParam(required = false) String ruleId, @RequestParam(required = false) String sceneCode,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of("ruleId", "sceneCode"));
        var records = ruleId == null || ruleId.isBlank()
                ? service.waitingRecords(userId(auth), sceneCode)
                : service.sources(userId(auth), id(ruleId));
        return ok(records);
    }

    /** Proposes one rule from the selected sources; read-only, nothing is written. */
    @PostMapping("/induce")
    public ResponseEntity<ApiResponse<RuleDraft>> induce(@RequestBody InduceRequest body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return ok(service.induce(userId(auth), body.interventionIds()));
    }

    public record InduceRequest(List<String> interventionIds) {}

    private static long id(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { throw com.aiscam.antiscam.common.error.ApiException.notFound(); }
    }

    private static long userId(Authentication auth) { return Long.parseLong(((Account) auth.getPrincipal()).id()); }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(data));
    }
}
