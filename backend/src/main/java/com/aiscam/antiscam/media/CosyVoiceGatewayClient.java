package com.aiscam.antiscam.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Calls the internal CosyVoice gateway in {@code tools/tts-gateway} (POST /v1/synthesize). */
public class CosyVoiceGatewayClient implements TtsClient {
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CosyVoiceGatewayClient(String baseUrl, ObjectMapper mapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.mapper = mapper;
    }

    @Override
    public SynthesizedAudio synthesize(String voiceId, double speed, List<SentenceSegmenter.Segment> segments, Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        var client = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
        try {
            String body = client.post().uri("/v1/synthesize").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("voiceId", voiceId, "speed", speed,
                            "segments", segments.stream().map(SentenceSegmenter.Segment::text).toList()))
                    .retrieve().body(String.class);
            JsonNode json = mapper.readTree(body);
            if (!"mp3".equals(json.path("format").asText())) throw new TtsException("unexpected audio format");
            byte[] mp3 = Base64.getDecoder().decode(json.path("audioBase64").asText());
            List<Long> durations = new ArrayList<>();
            json.path("segmentDurationsMs").forEach(n -> durations.add(n.asLong()));
            if (mp3.length == 0) throw new TtsException("empty audio");
            return TtsClient.assemble(mp3, segments, durations);
        } catch (TtsException e) {
            throw e;
        } catch (Exception e) {
            throw new TtsException("gateway call failed", e);
        }
    }
}
