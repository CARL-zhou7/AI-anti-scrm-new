package com.aiscam.antiscam.history.web;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.history.service.HistoryService;
import com.aiscam.antiscam.history.web.dto.HistoryViews.*;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anti.scam/v1/simulations")
public class HistoryController {
    private final HistoryService service;
    public HistoryController(HistoryService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<HistoryPage>> list(@RequestParam MultiValueMap<String, String> query,
            Authentication auth) {
        PageQuery.validate(query, Set.of("cursor", "limit"));
        var account = (Account) auth.getPrincipal();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(
                service.list(Long.parseLong(account.id()), account.id(), query.getFirst("cursor"), PageQuery.limit(query))));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<ApiResponse<HistoryDetail>> detail(@PathVariable String id,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(
                service.detail(Long.parseLong(((Account) auth.getPrincipal()).id()), id)));
    }
}
