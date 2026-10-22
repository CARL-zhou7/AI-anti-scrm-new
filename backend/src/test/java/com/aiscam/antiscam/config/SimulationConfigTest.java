package com.aiscam.antiscam.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class SimulationConfigTest {
    private SimulationProperties qwen() {
        var p=new SimulationProperties();p.getLlm().setApiKey("test-only-llm-key");
        p.getTts().setProvider("qwen");p.getTts().setQwenApiKey("test-only-qwen-key");
        p.getTts().setQwenBaseUrl("https://workspace.cn-beijing.maas.aliyuncs.com");return p;
    }
    @Test void productionAllowsConfiguredQwenButRejectsMissingCredentialsRegionAndUnknownProviders() {
        var p=qwen();var env=new MockEnvironment();
        assertDoesNotThrow(()->new SimulationConfig(p,env).guard());
        p.getTts().setQwenApiKey("");assertThrows(IllegalStateException.class,()->new SimulationConfig(p,env).guard());
        var noUrl=qwen();noUrl.getTts().setQwenBaseUrl("");assertThrows(IllegalStateException.class,()->new SimulationConfig(noUrl,env).guard());
        var region=qwen();region.getTts().setQwenRegion("singapore");assertThrows(IllegalStateException.class,()->new SimulationConfig(region,env).guard());
        var unknown=qwen();unknown.getTts().setProvider("unknown");assertThrows(IllegalStateException.class,()->new SimulationConfig(unknown,env).guard());
    }
    @Test void silentRemainsRestrictedToDevelopmentAndTest() {
        var p=new SimulationProperties();p.getLlm().setProvider("scripted");p.getTts().setProvider("silent");
        var dev=new MockEnvironment();dev.setActiveProfiles("dev");
        assertDoesNotThrow(()->new SimulationConfig(p,dev).guard());
        assertThrows(IllegalStateException.class,()->new SimulationConfig(p,new MockEnvironment()).guard());
    }
}
