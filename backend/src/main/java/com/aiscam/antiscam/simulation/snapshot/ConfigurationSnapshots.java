package com.aiscam.antiscam.simulation.snapshot;

import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.content.web.dto.AvatarRef;
import com.aiscam.antiscam.content.web.dto.ScammerView;
import com.aiscam.antiscam.content.web.dto.ScenarioView;
import com.aiscam.antiscam.content.web.dto.VictimView;
import com.aiscam.antiscam.content.web.dto.VictimScenarioProfileView;
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
    private static final ObjectMapper VERSION_JSON = new ObjectMapper();
    private final ObjectMapper mapper;
    public ConfigurationSnapshots(ObjectMapper mapper) { this.mapper = mapper; }

    public static boolean trustV2(com.aiscam.antiscam.simulation.domain.SimulationSession session) {
        return trustRuleset(session, "trust-v2");
    }

    /** V1.4. Exact match: trust-v2 sessions never become v3, and v3 sessions never fall back to v2. */
    public static boolean trustV3(com.aiscam.antiscam.simulation.domain.SimulationSession session) {
        return trustRuleset(session, "trust-v3");
    }

    private static boolean trustRuleset(com.aiscam.antiscam.simulation.domain.SimulationSession session, String version) {
        if (session.getRuntimeSnapshot() == null) return false;
        try {
            return version.equals(VERSION_JSON.readTree(session.getRuntimeSnapshot())
                    .path("trust_config").path("trust_ruleset_version").asText());
        } catch (java.io.IOException e) { throw new IllegalStateException("Unreadable runtime snapshot", e); }
    }

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

    public String victim(VictimProfile p, ScenarioCode selected) {
        VictimProfileValues.validateScenarios(p.getScenarioProfiles());
        Map<String, Object> m = common(p.getId(), p.getRowVersion(), p.getName(), p.getAvatarKey(), p.getSpeakingAvatarKey(),
                p.getAge(), p.getGender(), p.getIdentity(), p.getDescription());
        m.put("anti_fraud_awareness", p.getAntiFraudAwareness().name());
        m.put("risk_tendency", p.getRiskTendency().name());
        m.put("personality_traits", p.getPersonalityTraits());
        m.put("context_time", p.getContextTime());
        m.put("context_place", p.getContextPlace());
        m.put("current_state", p.getCurrentState());
        m.put("is_enabled", p.isEnabled());
        m.put("snapshot_schema_version", 3);
        m.put("selected_scenario_code", selected.name());
        m.put("family_status", p.getFamilyStatus());
        m.put("income_description", p.getIncomeDescription());
        m.put("monthly_expense_yuan", p.getMonthlyExpenseYuan());
        m.put("initial_savings_yuan", p.getInitialSavingsYuan());
        m.put("available_funds_yuan", p.getAvailableFundsYuan());
        m.put("reserved_funds_yuan", p.getReservedFundsYuan());
        m.put("general_reserve_funds_yuan", p.getGeneralReserveFundsYuan());
        m.put("emergency_reserve_funds_yuan", p.getEmergencyReserveFundsYuan());
        m.put("reserved_funds_note", p.getReservedFundsNote());
        m.put("general_decision_traits", p.getGeneralDecisionTraits());
        m.put("victim_scenario_profiles", p.getScenarioProfiles().stream().map(profile -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("scenario_code", profile.scenarioCode());
            fields.put("needs", profile.needs());
            fields.put("relevant_experience", profile.relevantExperience());
            fields.put("judgment_basis", profile.judgmentBasis());
            fields.put("verification_habits", profile.verificationHabits());
            fields.put("action_boundaries", profile.actionBoundaries());
            fields.put("stop_conditions", profile.stopConditions());
            fields.put("recent_events", profile.recentEvents());
            fields.put("consultations_transactions", profile.consultationsTransactions());
            fields.put("shared_experiences", profile.sharedExperiences());
            String contact = profile.contactBackground();
            if (selected == ScenarioCode.IMPERSONATION_LOAN && "男".equals(p.getGender())
                    && contact.matches("联系者声称是(?:外地工作的)?儿子")
                    && !p.getFamilyStatus().matches("(?s).*(随母姓|随母亲姓|不同姓|改姓|继子|养子).*")) {
                contact += "。本场既定家庭事实：父亲姓名为" + p.getName()
                        + "，儿子随父姓，双方都知道这一姓氏；儿子全名未设定，不得使用骗子画像的真实姓名代替。"
                        + "若画像明确记录不同姓氏或特殊家庭关系，以该明确事实为准。";
            }
            fields.put("contact_background", contact);
            fields.put("initial_trust_mode", profile.initialTrustMode());
            var parameters = p.scenarioProfile(ScenarioCode.valueOf(profile.scenarioCode()));
            fields.put("initial_trust_score", parameters.getInitialTrustScore());
            fields.put("positive_factor", parameters.getPositiveFactor());
            fields.put("negative_factor", parameters.getNegativeFactor());
            fields.put("small_amount_cap_yuan", parameters.getSmallAmountCapYuan());
            return fields;
        }).toList());
        return write(m);
    }

    public Names names(String scenarioJson, String scammerJson, String victimJson) {
        return new Names(read(scenarioJson).path("name").asText(), read(scammerJson).path("name").asText(),
                read(victimJson).path("name").asText());
    }

    public record Names(String scenario, String scammer, String victim) {}

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
                v.path("personality_traits").asText(), text(v, "family_status"), text(v, "income_description"),
                amount(v, "monthly_expense_yuan"), amount(v, "initial_savings_yuan"), amount(v, "available_funds_yuan"),
                amount(v, "reserved_funds_yuan"), text(v, "reserved_funds_note"), text(v, "general_decision_traits"),
                scenarioProfiles(v), v.path("snapshot_schema_version").asInt(1),
                v.path("snapshot_schema_version").asInt(1) >= 3 ? amount(v, "general_reserve_funds_yuan") : null,
                v.path("snapshot_schema_version").asInt(1) >= 3 ? amount(v, "emergency_reserve_funds_yuan") : null);
        return new ConfigurationView(scenario, scammer, victim);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Integer amount(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).intValue() : null;
    }

    private static java.util.List<VictimScenarioProfileView> scenarioProfiles(JsonNode victim) {
        var result = new java.util.ArrayList<VictimScenarioProfileView>();
        for (JsonNode p : victim.path("victim_scenario_profiles")) {
            result.add(new VictimScenarioProfileView(text(p, "scenario_code"), text(p, "needs"),
                    text(p, "relevant_experience"), text(p, "judgment_basis"), text(p, "verification_habits"),
                    text(p, "action_boundaries"), text(p, "stop_conditions"), text(p, "recent_events"),
                    text(p, "consultations_transactions"), text(p, "shared_experiences"), text(p, "contact_background"),
                    text(p, "initial_trust_mode")));
        }
        return result;
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
                + "\n性格特点：" + v.path("personality_traits").asText() + situation(v) + victimFacts(v);
    }

    public String victimPublicText(String json) {
        JsonNode v = read(json);
        StringBuilder text = new StringBuilder(base(v));
        for (String field : java.util.List.of("family_status", "income_description", "monthly_expense_yuan")) {
            if (v.hasNonNull(field)) text.append("\n").append(field).append("：").append(v.get(field).asText());
        }
        for (JsonNode profile : v.path("victim_scenario_profiles")) {
            if (!profile.path("scenario_code").asText().equals(v.path("selected_scenario_code").asText())) continue;
            for (String field : java.util.List.of("relevant_experience", "recent_events", "consultations_transactions", "shared_experiences", "contact_background")) {
                text.append("\n").append(field).append("：").append(profile.path(field).asText());
            }
        }
        return text.toString();
    }

    public String victimVisibleText(String json) {
        JsonNode v = read(json);
        var facts = new com.aiscam.antiscam.agent.VictimVisibleFacts(v.path("name").asText(), v.path("age").asInt(),
                v.path("gender").asText(), v.path("identity").asText(), v.path("description").asText(),
                v.path("family_status").asText(), v.path("income_description").asText(),
                v.path("general_decision_traits").asText(), v.path("current_state").asText(),
                java.util.List.of(), java.util.List.of(), java.util.List.of());
        StringBuilder text = new StringBuilder(write(facts));
        for (JsonNode profile : v.path("victim_scenario_profiles")) {
            if (!profile.path("scenario_code").asText().equals(v.path("selected_scenario_code").asText())) continue;
            for (String field : java.util.List.of("needs", "relevant_experience", "judgment_basis", "verification_habits",
                    "action_boundaries", "stop_conditions", "recent_events", "consultations_transactions", "shared_experiences", "contact_background")) {
                text.append("\n").append(field).append("：").append(profile.path(field).asText());
            }
        }
        return text.toString();
    }

    public String scammerVisibleText(String json) {
        JsonNode v = read(json);
        for (JsonNode profile : v.path("victim_scenario_profiles")) {
            if (profile.path("scenario_code").asText().equals(v.path("selected_scenario_code").asText())) {
                return write(new com.aiscam.antiscam.agent.ScammerVisibleProfile(v.path("age").asInt(), v.path("gender").asText(), v.path("identity").asText(),
                        v.path("description").asText(), profile.path("contact_background").asText(),
                        java.util.List.of(profile.path("shared_experiences").asText()), java.util.List.of()));
            }
        }
        return write(new com.aiscam.antiscam.agent.ScammerVisibleProfile(v.path("age").asInt(), v.path("gender").asText(), v.path("identity").asText(),
                v.path("description").asText(), "", java.util.List.of(), java.util.List.of()));
    }

    private static String victimFacts(JsonNode v) {
        if (v.path("snapshot_schema_version").asInt(1) < 2) return "";
        StringBuilder text = new StringBuilder();
        for (String field : java.util.List.of("family_status", "income_description", "monthly_expense_yuan", "initial_savings_yuan",
                "reserved_funds_note", "general_decision_traits")) text.append("\n").append(field).append("：").append(v.path(field).asText());
        for (JsonNode profile : v.path("victim_scenario_profiles")) {
            if (!profile.path("scenario_code").asText().equals(v.path("selected_scenario_code").asText())) continue;
            for (String field : java.util.List.of("needs", "relevant_experience", "judgment_basis", "verification_habits",
                    "action_boundaries", "stop_conditions", "recent_events", "consultations_transactions", "shared_experiences",
                    "contact_background", "initial_trust_mode")) text.append("\n").append(field).append("：").append(profile.path(field).asText());
        }
        return text.toString();
    }

    /**
     * Renders the rules frozen into {@code scene_rule_snapshot} as prompt lines. An absent, empty or unreadable
     * snapshot renders nothing, so a rule problem can never stop a session.
     */
    public String sceneRuleText(String json) {
        return sceneRuleText(json, "SCAMMER");
    }

    public String sceneRuleText(String json, String targetRole) {
        if (json == null || json.isBlank()) return "";
        com.fasterxml.jackson.databind.JsonNode node;
        try { node = read(json); } catch (IllegalStateException e) { return ""; }
        if (!node.isArray()) return "";
        StringBuilder text = new StringBuilder();
        for (var rule : node) {
            if (!targetRole.equals(rule.path("targetRole").asText("SCAMMER"))) continue;
            String body = rule.path("ruleText").asText("");
            if (body.isBlank()) continue;
            if (!text.isEmpty()) text.append('\n');
            String title = rule.path("title").asText("");
            if (!title.isBlank()) text.append('【').append(title.strip()).append('】');
            text.append(body.strip());
        }
        return text.toString();
    }

    /** Number of rules frozen at start; an absent or unreadable snapshot counts as none. */
    public int sceneRuleCount(String json) {
        if (json == null || json.isBlank()) return 0;
        try {
            var node = read(json);
            return node.isArray() ? node.size() : 0;
        } catch (IllegalStateException e) { return 0; }
    }

    public boolean fundsV3(com.aiscam.antiscam.simulation.domain.SimulationSession session) {
        return "funds-v3".equals(read(session.getRuntimeSnapshot()).path("fund_ruleset_version").asText());
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
