package com.aiscam.antiscam.media;

import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.media.TtsClient.AudioSegment;
import com.aiscam.antiscam.media.TtsClient.SynthesizedAudio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Temporary per-message audio: {@code {dir}/{sessionId}/{messageId}.mp3} plus a JSON sidecar with the duration and
 * segment timings. Nothing here is stored in the database; the whole session folder is released at a terminal state.
 */
@Component
public class TempAudioStore {
    private final Path root;
    private final ObjectMapper mapper;

    public TempAudioStore(SimulationProperties properties, ObjectMapper mapper) {
        this.root = Path.of(properties.getAudioTempDir()).toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public void write(long sessionId, long messageId, SynthesizedAudio audio) {
        try {
            Path folder = Files.createDirectories(root.resolve(Long.toString(sessionId)));
            atomicWrite(folder, messageId + ".mp3", audio.mp3());
            atomicWrite(folder, messageId + ".json", mapper.writeValueAsBytes(new Meta(audio.totalMs(), audio.segments())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Meta> meta(long sessionId, long messageId) {
        Path file = root.resolve(Long.toString(sessionId)).resolve(messageId + ".json");
        if (!Files.isRegularFile(file)) return Optional.empty();
        try { return Optional.of(mapper.readValue(file.toFile(), Meta.class)); }
        catch (IOException e) { return Optional.empty(); }
    }

    public Optional<byte[]> audio(long sessionId, long messageId) {
        Path file = root.resolve(Long.toString(sessionId)).resolve(messageId + ".mp3");
        if (!Files.isRegularFile(file)) return Optional.empty();
        try { return Optional.of(Files.readAllBytes(file)); }
        catch (IOException e) { return Optional.empty(); }
    }

    public void release(long sessionId) { deleteTree(root.resolve(Long.toString(sessionId))); }

    public void releaseAll() {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> children = Files.list(root)) { children.forEach(TempAudioStore::deleteTree); }
        catch (IOException ignored) { /* best effort at startup */ }
    }

    private static void atomicWrite(Path folder, String name, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(folder, ".audio-", ".tmp");
        Files.write(temporary, bytes);
        Files.move(temporary, folder.resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteTree(Path path) {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
        } catch (IOException ignored) { /* best effort */ }
    }

    public record Meta(long durationMs, List<AudioSegment> segments) {}
}
