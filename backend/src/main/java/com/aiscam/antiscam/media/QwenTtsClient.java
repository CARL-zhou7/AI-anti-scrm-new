package com.aiscam.antiscam.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Qwen-Audio-TTS HTTP MP3 synthesis; only the current sentence text is sent to the provider. */
public class QwenTtsClient implements TtsClient {
    private final URI endpoint;
    private final String model,apiKey;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public QwenTtsClient(String baseUrl,String model,String apiKey,ObjectMapper mapper) {
        this.endpoint=URI.create(baseUrl.replaceAll("/+$","")+"/api/v1/services/audio/tts/SpeechSynthesizer");
        this.model=model;this.apiKey=apiKey;this.mapper=mapper;
    }
    @Override public SynthesizedAudio synthesize(String voiceId,double speed,List<SentenceSegmenter.Segment> segments,Duration timeout) {
        if(segments.isEmpty()) throw new TtsException("No TTS segments");
        long deadline=System.nanoTime()+timeout.toNanos();
        var audio=new ByteArrayOutputStream();var durations=new ArrayList<Long>();
        int rate=0,channels=0;long samples=0,previousMs=0;
        try {
            for(var segment:segments) {
                byte[] json=mapper.writeValueAsBytes(Map.of("model",model,"input",Map.of("text",segment.text(),"voice",voiceId,"format","mp3","sample_rate",22050,"rate",speed)));
                var request=HttpRequest.newBuilder(endpoint).timeout(remaining(deadline)).header("Authorization","Bearer "+apiKey)
                        .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json)).build();
                var response=mapper.readTree(call(request,deadline));
                if(!"stop".equals(response.path("output").path("finish_reason").asText())) throw new TtsException("Qwen synthesis incomplete");
                URI url=URI.create(response.path("output").path("audio").path("url").asText());
                validateAudioUrl(url);
                var mp3=Mp3Frames.read(call(HttpRequest.newBuilder(url).timeout(remaining(deadline)).GET().build(),deadline));
                if(rate==0){rate=mp3.sampleRate();channels=mp3.channels();}
                else if(rate!=mp3.sampleRate()||channels!=mp3.channels()) throw new TtsException("Inconsistent MP3 segment format");
                audio.writeBytes(mp3.frames());samples+=mp3.samples();
                long totalMs=Math.round(samples*1000.0/rate);durations.add(totalMs-previousMs);previousMs=totalMs;
            }
            return TtsClient.assemble(audio.toByteArray(),segments,durations);
        } catch(TtsException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new TtsException("Qwen synthesis interrupted");}
        catch(Exception e){throw new TtsException("Qwen synthesis failed");}
    }
    private byte[] call(HttpRequest request,long deadline) throws Exception {
        var future=http.sendAsync(request,HttpResponse.BodyHandlers.ofByteArray());
        try {
            var response=future.get(remaining(deadline).toNanos(),TimeUnit.NANOSECONDS);
            if(response.statusCode()!=200) throw new TtsException("Qwen HTTP request failed: "+response.statusCode());
            if(response.body().length==0||response.body().length>10*1024*1024) throw new TtsException("Invalid Qwen response size");
            return response.body();
        } finally { if(!future.isDone())future.cancel(true); }
    }
    private static Duration remaining(long deadline) {
        long nanos=deadline-System.nanoTime();if(nanos<=0)throw new TtsException("Qwen synthesis timed out");return Duration.ofNanos(nanos);
    }
    private void validateAudioUrl(URI url) {
        String host=url.getHost();
        boolean provider="https".equals(url.getScheme())&&host!=null&&host.endsWith(".aliyuncs.com");
        boolean local="http".equals(endpoint.getScheme())&&("127.0.0.1".equals(endpoint.getHost())||"localhost".equals(endpoint.getHost()))
                &&"http".equals(url.getScheme())&&endpoint.getHost().equals(host)&&endpoint.getPort()==url.getPort();
        if(url.getUserInfo()!=null||(!provider&&!local))throw new TtsException("Invalid Qwen audio URL");
    }
}
