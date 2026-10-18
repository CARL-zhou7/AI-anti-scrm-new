package com.aiscam.antiscam.content.web;

import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.content.service.ContentService;
import com.aiscam.antiscam.content.web.dto.*;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import com.aiscam.antiscam.auth.web.dto.Account;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anti.scam/v1")
public class ContentController {
    private final ContentService content;
    public ContentController(ContentService content) { this.content = content; }

    @GetMapping("/scenarios")
    public ApiResponse<List<ScenarioView>> scenarios(@RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(content.listScenarios());
    }

    @GetMapping("/scammer-profiles")
    public ApiResponse<PageView<ScammerView>> scammers(@RequestParam MultiValueMap<String, String> query, Authentication auth) {
        return ApiResponse.ok(content.listScammers(PageQuery.from(query), auth.getName()));
    }

    @GetMapping("/victim-profiles")
    public ApiResponse<PageView<VictimView>> victims(@RequestParam MultiValueMap<String, String> query, Authentication auth) {
        return ApiResponse.ok(content.listVictims(PageQuery.from(query), auth.getName()));
    }

    @GetMapping("/scammer-profiles/{id}")
    public ApiResponse<ScammerView> scammer(@PathVariable String id, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(content.scammer(id));
    }

    @GetMapping("/victim-profiles/{id}")
    public ApiResponse<VictimView> victim(@PathVariable String id, @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(content.victim(id));
    }

    @GetMapping({"/avatars/{key}", "/avatars", "/avatars/"})
    public ResponseEntity<byte[]> avatar(@PathVariable(required = false) String key, @RequestParam MultiValueMap<String, String> query,
            Authentication auth) {
        PageQuery.validate(query, Set.of());
        var resource = content.avatar(key, Long.parseLong(((Account) auth.getPrincipal()).id()));
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff").header("Content-Type", resource.mediaType()).body(resource.bytes());
    }
}
