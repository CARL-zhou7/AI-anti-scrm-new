package com.aiscam.antiscam.admin.web;

import com.aiscam.antiscam.admin.service.AdminProfileService;
import com.aiscam.antiscam.admin.web.dto.*;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.content.web.dto.PageView;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anti.scam/v1/admin")
public class AdminProfileController {
    private final AdminProfileService service;
    public AdminProfileController(AdminProfileService service) { this.service = service; }
    private PageQuery page(MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of("cursor", "limit"));
        return PageQuery.from(query);
    }

    @DeleteMapping("/scammer-profiles/{id}")
    public ApiResponse<java.util.Map<String, Boolean>> deleteScammer(@PathVariable String id, @Valid @RequestBody VersionRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        service.deleteScammer(id, body);
        return ApiResponse.ok(java.util.Map.of("deleted", true));
    }

    @DeleteMapping("/victim-profiles/{id}")
    public ApiResponse<java.util.Map<String, Boolean>> deleteVictim(@PathVariable String id, @Valid @RequestBody VersionRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        service.deleteVictim(id, body);
        return ApiResponse.ok(java.util.Map.of("deleted", true));
    }

    @GetMapping("/scammer-profiles")
    public ApiResponse<PageView<AdminScammerView>> listScammers(@RequestParam MultiValueMap<String, String> query, Authentication auth) {
        return ApiResponse.ok(service.listScammers(page(query), auth.getName()));
    }

    @GetMapping("/scammer-profiles/{id}")
    public ApiResponse<AdminScammerView> getScammer(@PathVariable String id, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.getScammer(id));
    }

    @PostMapping("/scammer-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminScammerView> createScammer(@Valid @RequestBody ScammerWriteRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.createScammer(body));
    }

    @PutMapping("/scammer-profiles/{id}")
    public ApiResponse<AdminScammerView> updateScammer(@PathVariable String id, @Valid @RequestBody ScammerUpdateRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.updateScammer(id, body));
    }

    @PostMapping("/scammer-profiles/{id}/enable")
    public ApiResponse<AdminScammerView> enableScammer(@PathVariable String id, @Valid @RequestBody VersionRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.setScammerEnabled(id, body, true));
    }

    @PostMapping("/scammer-profiles/{id}/disable")
    public ApiResponse<AdminScammerView> disableScammer(@PathVariable String id, @Valid @RequestBody VersionRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.setScammerEnabled(id, body, false));
    }

    @GetMapping("/victim-profiles")
    public ApiResponse<PageView<AdminVictimView>> listVictims(@RequestParam MultiValueMap<String, String> query, Authentication auth) {
        return ApiResponse.ok(service.listVictims(page(query), auth.getName()));
    }

    @GetMapping("/victim-profiles/{id}")
    public ApiResponse<AdminVictimView> getVictim(@PathVariable String id, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.getVictim(id));
    }

    @PostMapping("/victim-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminVictimView> createVictim(@Valid @RequestBody VictimWriteRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.createVictim(body));
    }

    @PutMapping("/victim-profiles/{id}")
    public ApiResponse<AdminVictimView> updateVictim(@PathVariable String id, @Valid @RequestBody VictimUpdateRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.updateVictim(id, body));
    }

    @PostMapping("/victim-profiles/{id}/enable")
    public ApiResponse<AdminVictimView> enableVictim(@PathVariable String id, @Valid @RequestBody VersionRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.setVictimEnabled(id, body, true));
    }

    @PostMapping("/victim-profiles/{id}/disable")
    public ApiResponse<AdminVictimView> disableVictim(@PathVariable String id, @Valid @RequestBody VersionRequest body, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.setVictimEnabled(id, body, false));
    }
}
