package com.aiscam.antiscam.admin.web;

import com.aiscam.antiscam.admin.service.AdminScenarioService;
import com.aiscam.antiscam.admin.web.dto.*;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anti.scam/v1/admin/scenarios")
public class AdminScenarioController {
    private final AdminScenarioService service;
    public AdminScenarioController(AdminScenarioService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<AdminScenarioView>> list(@RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.list());
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminScenarioView> update(@PathVariable String id, @Valid @RequestBody ScenarioUpdateRequest body,
            @RequestParam MultiValueMap<String, String> query) {
        PageQuery.validate(query, Set.of());
        return ApiResponse.ok(service.update(id, body));
    }
}
