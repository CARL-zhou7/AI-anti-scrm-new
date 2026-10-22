package com.aiscam.antiscam.media;

import com.aiscam.antiscam.config.SimulationProperties;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps profile gender text and age band to preset voices (PRD §11.3). Each (age band, gender) has a primary and a
 * backup voice; when both roles land on the same voice the victim takes the backup. Missing voices fail at startup.
 */
@Component
public class VoiceMapper {
    public static final String MAPPING_VERSION = "2";
    private final Map<String, String> voices = new HashMap<>();
    private final SimulationProperties properties;

    public VoiceMapper(SimulationProperties properties) { this.properties = properties; }

    @PostConstruct
    void load() {
        voices.clear();
        for (var voice : properties.getTts().getVoices()) {
            String id = voice.getId();
            if (id == null || id.isBlank() || voices.putIfAbsent(key(voice.getAgeGroup(), voice.getGender(), voice.getRank()), id) != null) {
                throw new IllegalStateException("TTS voices require nonempty IDs and unique mapping keys");
            }
        }
        for (String age : new String[]{"YOUTH", "MIDDLE", "SENIOR"}) {
            for (String gender : new String[]{"MALE", "FEMALE", "NEUTRAL"}) {
                for (String rank : new String[]{"PRIMARY", "BACKUP"}) {
                    if (!voices.containsKey(key(age, gender, rank))) {
                        throw new IllegalStateException("Missing TTS voice for " + key(age, gender, rank));
                    }
                }
                if (voices.get(key(age,gender,"PRIMARY")).equals(voices.get(key(age,gender,"BACKUP")))) throw new IllegalStateException("TTS primary and backup must differ for " + age + "_" + gender);
            }
        }
        if (voices.size() != 18) throw new IllegalStateException("Exactly 18 TTS voice mappings are required");
    }

    public VoicePair map(int scammerAge, String scammerGender, int victimAge, String victimGender) {
        String scammer = voices.get(key(ageGroup(scammerAge), gender(scammerGender), "PRIMARY"));
        String victim = voices.get(key(ageGroup(victimAge), gender(victimGender), "PRIMARY"));
        if (victim.equals(scammer)) victim = voices.get(key(ageGroup(victimAge), gender(victimGender), "BACKUP"));
        return new VoicePair(scammer, victim, MAPPING_VERSION);
    }

    /** Youth 18-34 (under-18 profiles also use youth voices, decision D-4), middle 35-59, senior 60+. */
    static String ageGroup(int age) { return age < 35 ? "YOUTH" : age < 60 ? "MIDDLE" : "SENIOR"; }

    static String gender(String text) { return "男".equals(text) ? "MALE" : "女".equals(text) ? "FEMALE" : "NEUTRAL"; }

    private static String key(String age, String gender, String rank) { return age + "_" + gender + "_" + rank; }

    public record VoicePair(String scammerVoice, String victimVoice, String mappingVersion) {}
}
