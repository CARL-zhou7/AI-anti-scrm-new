package com.aiscam.antiscam.admin.web;

import com.aiscam.antiscam.admin.service.TrustPolicyService;
import com.aiscam.antiscam.admin.web.dto.TrustPolicyViews.*;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.content.page.PageQuery;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anti.scam/v1/admin/trust-policy")
public class TrustPolicyController {
    private final TrustPolicyService service;
    public TrustPolicyController(TrustPolicyService service) { this.service=service; }
    @GetMapping
    public ApiResponse<View> get(@RequestParam MultiValueMap<String,String> query) {
        PageQuery.validate(query,Set.of()); return ApiResponse.ok(service.get());
    }
    @PutMapping
    public ApiResponse<View> update(@Valid @RequestBody Update body,@RequestParam MultiValueMap<String,String> query) {
        PageQuery.validate(query,Set.of()); return ApiResponse.ok(service.update(body));
    }
}
