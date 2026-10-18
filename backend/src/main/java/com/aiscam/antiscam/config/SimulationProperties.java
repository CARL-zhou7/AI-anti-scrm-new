package com.aiscam.antiscam.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.simulation.*}. Values marked TBD in the plan may be tuned, but never their meaning. */
@ConfigurationProperties("app.simulation")
public class SimulationProperties {
    private final Timing timing = new Timing();
    private final Llm llm = new Llm();
    private final Tts tts = new Tts();
    private final Sse sse = new Sse();
    private final Connection connection = new Connection();
    private final Executors executors = new Executors();
    private String audioTempDir = "var/tmp-audio";
    private long reportAttemptTimeoutMs = 120_000;

    public Timing getTiming() { return timing; }
    public Llm getLlm() { return llm; }
    public Tts getTts() { return tts; }
    public Sse getSse() { return sse; }
    public Connection getConnection() { return connection; }
    public Executors getExecutors() { return executors; }
    public String getAudioTempDir() { return audioTempDir; }
    public void setAudioTempDir(String value) { audioTempDir = value; }
    public long getReportAttemptTimeoutMs() { return reportAttemptTimeoutMs; }
    public void setReportAttemptTimeoutMs(long value) { reportAttemptTimeoutMs = value; }

    public static class Timing {
        private long sampleTtlMs = 60_000;
        private long maxUncertaintyMs = 1_500;
        private long maxClickToArrivalMs = 30_000;
        public long getSampleTtlMs() { return sampleTtlMs; }
        public void setSampleTtlMs(long v) { sampleTtlMs = v; }
        public long getMaxUncertaintyMs() { return maxUncertaintyMs; }
        public void setMaxUncertaintyMs(long v) { maxUncertaintyMs = v; }
        public long getMaxClickToArrivalMs() { return maxClickToArrivalMs; }
        public void setMaxClickToArrivalMs(long v) { maxClickToArrivalMs = v; }
    }

    public static class Llm {
        private String provider = "deepseek";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "";
        private String apiKey = "";
        private long callTimeoutMs = 20_000;
        private int transientRetries = 2;
        private int maxTokens = 800;
        private double scammerTemperature = 0.9;
        private double victimTemperature = 0.8;
        private double narratorTemperature = 0.2;
        private double reportTemperature = 0.3;
        public String getProvider() { return provider; }
        public void setProvider(String v) { provider = v; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { baseUrl = v; }
        public String getModel() { return model; }
        public void setModel(String v) { model = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { apiKey = v; }
        public long getCallTimeoutMs() { return callTimeoutMs; }
        public void setCallTimeoutMs(long v) { callTimeoutMs = v; }
        public int getTransientRetries() { return transientRetries; }
        public void setTransientRetries(int v) { transientRetries = v; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int v) { maxTokens = v; }
        public double getScammerTemperature() { return scammerTemperature; }
        public void setScammerTemperature(double v) { scammerTemperature = v; }
        public double getVictimTemperature() { return victimTemperature; }
        public void setVictimTemperature(double v) { victimTemperature = v; }
        public double getNarratorTemperature() { return narratorTemperature; }
        public void setNarratorTemperature(double v) { narratorTemperature = v; }
        public double getReportTemperature() { return reportTemperature; }
        public void setReportTemperature(double v) { reportTemperature = v; }
    }

    public static class Tts {
        private String provider = "cosyvoice";
        private String gatewayUrl = "http://127.0.0.1:50080";
        private long callTimeoutMs = 30_000;
        private List<Voice> voices = new ArrayList<>();
        public String getProvider() { return provider; }
        public void setProvider(String v) { provider = v; }
        public String getGatewayUrl() { return gatewayUrl; }
        public void setGatewayUrl(String v) { gatewayUrl = v; }
        public long getCallTimeoutMs() { return callTimeoutMs; }
        public void setCallTimeoutMs(long v) { callTimeoutMs = v; }
        public List<Voice> getVoices() { return voices; }
        public void setVoices(List<Voice> v) { voices = v; }
    }

    public static class Voice {
        private String id;
        private String ageGroup;
        private String gender;
        private String rank;
        public String getId() { return id; }
        public void setId(String v) { id = v; }
        public String getAgeGroup() { return ageGroup; }
        public void setAgeGroup(String v) { ageGroup = v; }
        public String getGender() { return gender; }
        public void setGender(String v) { gender = v; }
        public String getRank() { return rank; }
        public void setRank(String v) { rank = v; }
    }

    public static class Sse {
        private long heartbeatMs = 15_000;
        private long emitterTimeoutMs = 1_800_000;
        public long getHeartbeatMs() { return heartbeatMs; }
        public void setHeartbeatMs(long v) { heartbeatMs = v; }
        public long getEmitterTimeoutMs() { return emitterTimeoutMs; }
        public void setEmitterTimeoutMs(long v) { emitterTimeoutMs = v; }
    }

    public static class Connection {
        private long lostGraceMs = 30_000;
        public long getLostGraceMs() { return lostGraceMs; }
        public void setLostGraceMs(long v) { lostGraceMs = v; }
    }

    public static class Executors {
        private int lanes = 8;
        public int getLanes() { return lanes; }
        public void setLanes(int v) { lanes = v; }
    }
}
