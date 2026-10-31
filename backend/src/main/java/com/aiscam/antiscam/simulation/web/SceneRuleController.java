package com.aiscam.antiscam.simulation.web;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.simulation.service.SceneRuleService;
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.InterventionRecord;
import com.aiscam.antiscam.simulation.web.dto.SceneRuleViews.*;
import java.util.List;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/** Scenario knowledge base (interface spec API-52～54): list, create, update and source lookup. */
@RestController
@RequestMapping("/anti.scam/v1/admin/scene-rules")
public class SceneRuleController {
    private final SceneRuleService service;

    public SceneRuleController(SceneRuleService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<SceneRulePage>> list(@RequestParam MultiValueMap<String, String> query,
            Authentication auth) {
        PageQuery.validate(query, Set.of());
        return ok(new SceneRulePage(service.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SceneRuleView>> create(@RequestBody SceneRuleWrite body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.create(userId(auth), body)));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<ApiResponse<SceneRuleView>> update(@PathVariable String ruleId,
            @RequestBody SceneRuleUpdate body, @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return ok(service.update(userId(auth), id(ruleId), body));
    }

    /** Sources of one rule, limited to the caller's own adopted interventions. */
    @GetMapping("/{ruleId}/sources")
    public ResponseEntity<ApiResponse<List<InterventionRecord>>> sources(@PathVariable String ruleId,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return ok(service.sources(userId(auth), id(ruleId)));
    }

    private static long id(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { throw com.aiscam.antiscam.common.error.ApiException.notFound(); }
    }

    private static long userId(Authentication auth) { return Long.parseLong(((Account) auth.getPrincipal()).id()); }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(data));
    }
}
