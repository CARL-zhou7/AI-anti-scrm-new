package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.domain.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Manual dialogue may change the story; actual financial actions are validated when saving/adopting the draft. */
@Component
public class DraftContentValidator {
    public void validate(SimulationSession session, SimulationScammerDraft draft, String candidate,
            List<SimulationMessage> history) {
        String content = candidate == null ? "" : candidate.strip();
        if (content.isEmpty() || content.codePointCount(0, content.length()) > 2000)
            throw ApiException.field("content", "CONTENT_REJECTED", "内容须为1至2000个字符");
    }
}
