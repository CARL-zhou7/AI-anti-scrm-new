package com.aiscam.antiscam.media;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev/test synthesizer: real, decodable silent MP3 (MPEG-1 Layer III, 32 kbps, 44.1 kHz, mono; 104-byte frames of
 * 1152 samples ≈ 26.122 ms) sized to max(600, codePoints × 200) ms per segment. A line containing the marker
 * {@code [TTS_FAIL]} fails, so scripted runs can exercise the TTS_FAILED path.
 */
public class SilentTtsClient implements TtsClient {
    public static final String FAIL_MARKER = "[TTS_FAIL]";
    private static final double FRAME_MS = 1152 * 1000.0 / 44_100;

    @Override
    public SynthesizedAudio synthesize(String voiceId, double speed, List<SentenceSegmenter.Segment> segments, Duration timeout) {
        List<Long> durations = new ArrayList<>();
        for (var segment : segments) {
            if (segment.text().contains(FAIL_MARKER)) throw new TtsException("scripted synthesis failure");
            durations.add((long) Math.max(600, segment.text().codePointCount(0, segment.text().length()) * 200));
        }
        long total = durations.stream().mapToLong(Long::longValue).sum();
        int frames = (int) Math.ceil(total / FRAME_MS);
        byte[] mp3 = new byte[frames * 104];
        for (int f = 0; f < frames; f++) {
            mp3[f * 104] = (byte) 0xFF;
            mp3[f * 104 + 1] = (byte) 0xFB;
            mp3[f * 104 + 2] = (byte) 0x10;
            mp3[f * 104 + 3] = (byte) 0xC0;
        }
        return TtsClient.assemble(mp3, segments, durations);
    }
}
