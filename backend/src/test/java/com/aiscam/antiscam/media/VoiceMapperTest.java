package com.aiscam.antiscam.media;
import com.aiscam.antiscam.config.SimulationProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VoiceMapperTest {
    @Test void confirmedApplicationDefaultsBindAllSlotsAndReuseNeutralVoices() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=classpath:/application.yml", "spring.config.import=", "TTS_PROVIDER=qwen")
                .run(context -> {
                    var p=org.springframework.boot.context.properties.bind.Binder.get(context.getEnvironment())
                            .bind("app.simulation",SimulationProperties.class).get();
                    assertEquals(18,p.getTts().getVoices().size());
                    var mapper=new VoiceMapper(p);mapper.load();
                    assertEquals("libai_v3.1",mapper.map(22,"男",66,"男").victimVoice());
                    assertEquals("longanlang_v3.1",mapper.map(22,"男",22,"其他").victimVoice());
                    assertEquals("anruorou_v3.1",mapper.map(40,"女",66,"女").victimVoice());
                });
    }
    private SimulationProperties properties() {
        var p = new SimulationProperties();
        for (String age : new String[]{"YOUTH","MIDDLE","SENIOR"}) for (String gender : new String[]{"MALE","FEMALE","NEUTRAL"})
            for (String rank : new String[]{"PRIMARY","BACKUP"}) {
                var v = new SimulationProperties.Voice();v.setAgeGroup(age);v.setGender(gender);v.setRank(rank);v.setId(age+"_"+gender+"_"+rank);p.getTts().getVoices().add(v);
            }
        return p;
    }
    @Test void mapsAllEighteenKeysAndUsesDistinctBackupForMatchingRoles() {
        var mapper = new VoiceMapper(properties());mapper.load();
        assertEquals("2",VoiceMapper.MAPPING_VERSION);
        var pair = mapper.map(22,"男",30,"男");
        assertEquals("YOUTH_MALE_PRIMARY",pair.scammerVoice());assertEquals("YOUTH_MALE_BACKUP",pair.victimVoice());
        assertEquals("2",pair.mappingVersion());
        assertEquals("MIDDLE_FEMALE_PRIMARY",mapper.map(35,"女",60,"其他").scammerVoice());
        assertEquals("SENIOR_NEUTRAL_PRIMARY",mapper.map(35,"女",60,"其他").victimVoice());
    }
    @Test void missingBlankDuplicateAndIdenticalBackupAreRejected() {
        var missing=properties();missing.getTts().getVoices().removeLast();
        assertThrows(IllegalStateException.class,()->new VoiceMapper(missing).load());
        var blank=properties();blank.getTts().getVoices().getFirst().setId(" ");
        assertThrows(IllegalStateException.class,()->new VoiceMapper(blank).load());
        var duplicate=properties();duplicate.getTts().getVoices().add(duplicate.getTts().getVoices().getFirst());
        assertThrows(IllegalStateException.class,()->new VoiceMapper(duplicate).load());
        var same=properties();same.getTts().getVoices().get(1).setId(same.getTts().getVoices().getFirst().getId());
        assertThrows(IllegalStateException.class,()->new VoiceMapper(same).load());
    }
}
