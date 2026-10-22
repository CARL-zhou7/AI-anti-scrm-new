package com.aiscam.antiscam.media;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class QwenTtsClientTest {
    private HttpServer server;private String base;private final ObjectMapper json=new ObjectMapper();
    private final AtomicInteger calls=new AtomicInteger();private volatile byte[] audio;
    private volatile int status=200;private volatile String audioUrl;private volatile long delay;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+server.getAddress().getPort();
        audioUrl=base+"/audio";audio=frames(3);server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/v1/services/audio/tts/SpeechSynthesizer",exchange->{
            calls.incrementAndGet();var request=json.readTree(exchange.getRequestBody());
            assertEquals("Bearer test-only-key",exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("qwen-audio-3.1-tts-flash",request.path("model").asText());
            assertEquals("mp3",request.path("input").path("format").asText());
            assertEquals(1.1,request.path("input").path("rate").asDouble());
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            var body=json.writeValueAsBytes(Map.of("output",Map.of("finish_reason","stop","audio",Map.of("url",audioUrl))));
            exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/audio",exchange->{assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type","audio/mpeg");exchange.sendResponseHeaders(200,audio.length);exchange.getResponseBody().write(audio);exchange.close();});
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private QwenTtsClient client() { return new QwenTtsClient(base,"qwen-audio-3.1-tts-flash","test-only-key",json); }
    static byte[] frames(int count) {byte[] bytes=new byte[count*104];for(int i=0;i<count;i++){bytes[i*104]=(byte)255;bytes[i*104+1]=(byte)251;bytes[i*104+2]=16;bytes[i*104+3]=(byte)192;}return bytes;}
    @Test void joinsRealMp3FramesAndMatchesEachSentenceToItsAudioDuration() {
        var segments=SentenceSegmenter.split("你好，下一句。");var result=client().synthesize("test-voice",1.1,segments,Duration.ofSeconds(3));
        assertEquals(2,calls.get());assertEquals(6*104,result.mp3().length);assertEquals(2,result.segments().size());
        assertEquals(78,result.segments().getFirst().endMs());assertEquals(78,result.segments().get(1).startMs());
        assertEquals(157,result.totalMs());assertEquals(segments.get(1).charEnd(),result.segments().get(1).charEnd());
    }
    @Test void rejectsHttpFailureNonMp3TruncatedAudioAndUnsafeDownloadUrl() {
        var segments=SentenceSegmenter.split("你好。");
        status=401;assertThrows(TtsClient.TtsException.class,()->client().synthesize("test-voice",1.1,segments,Duration.ofSeconds(2)));
        status=200;audio="RIFF-not-mp3".getBytes();assertThrows(TtsClient.TtsException.class,()->client().synthesize("test-voice",1.1,segments,Duration.ofSeconds(2)));
        audio=java.util.Arrays.copyOf(frames(3),200);assertThrows(TtsClient.TtsException.class,()->client().synthesize("test-voice",1.1,segments,Duration.ofSeconds(2)));
        audioUrl="file:///private";assertThrows(TtsClient.TtsException.class,()->client().synthesize("test-voice",1.1,segments,Duration.ofSeconds(2)));
    }
    @Test void timeoutFailsWithoutReturningPartialAudio() {
        delay=200;assertThrows(TtsClient.TtsException.class,()->client().synthesize("test-voice",1.1,SentenceSegmenter.split("你好。"),Duration.ofMillis(30)));
    }
}
