package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.agent.AgentModels.EvidenceItem;
import com.aiscam.antiscam.agent.AgentModels.EvidenceSource;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.aiscam.antiscam.simulation.rules.FactCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saved evidence_json items: fact_code, message_id, event_order, quote (+ round_no, speaker, source for traceability). */
public final class EvidenceJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private EvidenceJson() {}

    public static String write(List<EvidenceItem> items, Map<String, Long> messageIds) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EvidenceItem e : items) {
            Long messageId = messageIds.get(e.roundNo() + ":" + e.speaker());
            if (messageId == null) throw new IllegalStateException("evidence cites an unsaved message");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fact_code", e.factCode().name());
            m.put("message_id", messageId.toString());
            m.put("event_order", e.eventOrder());
            m.put("quote", e.quote());
            m.put("round_no", e.roundNo());
            m.put("speaker", e.speaker().name());
            m.put("source", e.source().name());
            out.add(m);
        }
        try { return MAPPER.writeValueAsString(out); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static List<EvidenceItem> read(String json) {
        if (json == null) return List.of();
        try {
            List<EvidenceItem> items = new ArrayList<>();
            for (JsonNode n : MAPPER.readTree(json)) {
                items.add(new EvidenceItem(FactCode.valueOf(n.path("fact_code").asText()),
                        SpeakerRole.valueOf(n.path("speaker").asText()), EvidenceSource.valueOf(n.path("source").asText()),
                        n.path("round_no").asInt(), n.path("quote").asText(), n.path("event_order").asInt()));
            }
            return items;
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable evidence", e);
        }
    }
}
