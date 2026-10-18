package com.aiscam.antiscam.simulation.snapshot;

import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.content.web.dto.AvatarRef;
import com.aiscam.antiscam.content.web.dto.ScammerView;
import com.aiscam.antiscam.content.web.dto.ScenarioView;
import com.aiscam.antiscam.content.web.dto.VictimView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Complete configuration snapshots (DB design §10.2) frozen at start. Views are always rebuilt from the snapshot,
 * never from the current profile tables, so later edits or disabling never change a recorded run.
 */
@Component
public class ConfigurationSnapshots {
    private final ObjectMapper mapper;
    public ConfigurationSnapshots(ObjectMapper mapper) { this.mapper = mapper; }

    public String scenario(FraudScenario s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId().toString());
        m.put("scenario_code", s.getScenarioCode().name());
        m.put("name", s.getName());
        m.put("description", s.getDescription());
        m.put("row_version", s.getRowVersion());
        m.put("focus", s.getScenarioCode().focus());
        return write(m);
    }

    public String scammer(ScammerProfile p) {
        Map<String, Object> m = common(p.getId(), p.getRowVersion(), p.getName(), p.getAvatarKey(), p.getSpeakingAvatarKey(),
                p.getAge(), p.getGender(), p.getIdentity(), p.getDescription());
        m.put("fraud_experience", p.getFraudExperience().name());
        m.put("speech_skill", p.getSpeechSkill().name());
        m.put("manipulation_skill", p.getManipulationSkill().name());
        m.put("behavior_traits", p.getBehaviorTraits());
        m.put("context_time", p.getContextTime());
        m.put("context_place", p.getContextPlace());
        m.put("current_state", p.getCurrentState());
        m.put("is_enabled", p.isEnabled());
        return write(m);
    }

    public String victim(VictimProfile p) {
        Map<String, Object> m = common(p.getId(), p.getRowVersion(), p.getName(), p.getAvatarKey(), p.getSpeakingAvatarKey(),
                p.getAge(), p.getGender(), p.getIdentity(), p.getDescription());
        m.put("anti_fraud_awareness", p.getAntiFraudAwareness().name());
        m.put("risk_tendency", p.getRiskTendency().name());
        m.put("personality_traits", p.getPersonalityTraits());
        m.put("context_time", p.getContextTime());
        m.put("context_place", p.getContextPlace());
        m.put("current_state", p.getCurrentState());
        m.put("is_enabled", p.isEnabled());
        return write(m);
    }

    public ConfigurationView toConfigurationView(String scenarioJson, String scammerJson, String victimJson) {
        JsonNode s = read(scenarioJson), c = read(scammerJson), v = read(victimJson);
        var scenario = new ScenarioView(s.path("id").asText(), ScenarioCode.valueOf(s.path("scenario_code").asText()),
                s.path("name").asText(), s.path("description").asText(), s.path("focus").asText());
        var scammer = new ScammerView(c.path("id").asText(), AvatarRef.from(c.path("avatar_key").asText()),
                AvatarRef.from(c.path("speaking_avatar_key").asText()), c.path("name").asText(), c.path("age").asInt(),
                c.path("gender").asText(), c.path("identity").asText(), c.path("description").asText(),
                c.path("context_time").asText(), c.path("context_place").asText(), c.path("current_state").asText(),
                Level.valueOf(c.path("fraud_experience").asText()), Level.valueOf(c.path("speech_skill").asText()),
                Level.valueOf(c.path("manipulation_skill").asText()), c.path("behavior_traits").asText());
        var victim = new VictimView(v.path("id").asText(), AvatarRef.from(v.path("avatar_key").asText()),
                AvatarRef.from(v.path("speaking_avatar_key").asText()), v.path("name").asText(), v.path("age").asInt(),
                v.path("gender").asText(), v.path("identity").asText(), v.path("description").asText(),
                v.path("context_time").asText(), v.path("context_place").asText(), v.path("current_state").asText(),
                Level.valueOf(v.path("anti_fraud_awareness").asText()), Level.valueOf(v.path("risk_tendency").asText()),
                v.path("personality_traits").asText());
        return new ConfigurationView(scenario, scammer, victim);
    }

    /** Profile text handed to the Agents. */
    public String scammerText(String json) {
        JsonNode c = read(json);
        return base(c) + "\n诈骗经验：" + level(c.path("fraud_experience").asText()) + "\n话术能力：" + level(c.path("speech_skill").asText())
                + "\n心理操控能力：" + level(c.path("manipulation_skill").asText()) + "\n行为特点：" + c.path("behavior_traits").asText()
                + situation(c);
    }

    public String victimText(String json) {
        JsonNode v = read(json);
        return base(v) + "\n防骗意识：" + level(v.path("anti_fraud_awareness").asText()) + "\n风险倾向：" + level(v.path("risk_tendency").asText())
                + "\n性格特点：" + v.path("personality_traits").asText() + situation(v);
    }

    public JsonNode read(String json) {
        try { return mapper.readTree(json); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Unreadable snapshot", e); }
    }

    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private static Map<String, Object> common(Long id, int rowVersion, String name, String avatarKey, String speakingKey,
            Integer age, String gender, String identity, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id.toString());
        m.put("row_version", rowVersion);
        m.put("name", name);
        m.put("avatar_key", avatarKey);
        m.put("speaking_avatar_key", speakingKey);
        m.put("age", age);
        m.put("gender", gender);
        m.put("identity", identity);
        m.put("description", description);
        return m;
    }

    private static String base(JsonNode p) {
        return "姓名：" + p.path("name").asText() + "\n年龄：" + p.path("age").asInt() + "岁\n性别：" + p.path("gender").asText()
                + "\n身份：" + p.path("identity").asText() + "\n人物描述：" + p.path("description").asText();
    }

    private static String situation(JsonNode p) {
        return "\n情境时间：" + p.path("context_time").asText() + "\n情境地点：" + p.path("context_place").asText()
                + "\n当前状态：" + p.path("current_state").asText();
    }

    private static String level(String level) { return switch (level) { case "LOW" -> "低"; case "MEDIUM" -> "中"; default -> "高"; }; }

    public record ConfigurationView(ScenarioView scenario, ScammerView scammer, VictimView victim) {}
}
