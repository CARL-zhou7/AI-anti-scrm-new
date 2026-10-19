package com.aiscam.antiscam.report.web;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.common.web.RequestContent;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.report.service.ReportService;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.ReportView;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anti.scam/v1/simulations")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/{id}/report")
    public ResponseEntity<ApiResponse<ReportView>> view(@PathVariable String id,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(service.view(userId(auth), id)));
    }

    @PostMapping("/{id}/report/retry")
    public ResponseEntity<ApiResponse<ReportView>> retry(@PathVariable String id, @RequestBody(required = false) String body,
            @RequestParam MultiValueMap<String, String> query, Authentication auth) {
        PageQuery.validate(query, Set.of());
        RequestContent.rejectBody(body);
        var result = service.retry(userId(auth), id);
        return ResponseEntity.status(result.httpStatus()).header("Cache-Control", "no-store").body(ApiResponse.ok(result.view()));
    }

    private static long userId(Authentication auth) { return Long.parseLong(((Account) auth.getPrincipal()).id()); }
}
