package com.aiscam.antiscam.timing.web;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.common.time.UtcTime;
import com.aiscam.antiscam.content.page.PageQuery;
import com.aiscam.antiscam.timing.TimingSampleStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anti.scam/v1/timing")
public class TimingController {
    private final TimingSampleStore samples;
    public TimingController(TimingSampleStore samples) { this.samples = samples; }

    @GetMapping("/samples")
    public ResponseEntity<ApiResponse<TimingSampleView>> sample(@RequestParam MultiValueMap<String, String> query,
            Authentication auth, HttpServletRequest request) {
        PageQuery.validate(query, Set.of());
        var sample = samples.issue(request.getSession().getId(), Long.parseLong(((Account) auth.getPrincipal()).id()));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(new TimingSampleView(
                sample.sampleId(), UtcTime.format(sample.serverSampledAt()), UtcTime.format(sample.expiresAt()),
                sample.methodVersion())));
    }

    public record TimingSampleView(String sampleId, String serverSampledAt, String expiresAt, String methodVersion) {}
}
