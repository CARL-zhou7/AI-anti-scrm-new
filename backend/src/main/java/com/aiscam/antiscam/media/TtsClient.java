package com.aiscam.antiscam.media;

import java.time.Duration;
import java.util.List;

/** Synthesizes one line into a single MP3 with per-segment timings. Never called inside a DB transaction. */
public interface TtsClient {
    SynthesizedAudio synthesize(String voiceId, double speed, List<SentenceSegmenter.Segment> segments, Duration timeout);

    record AudioSegment(long startMs, long endMs, int charStart, int charEnd) {}

    record SynthesizedAudio(byte[] mp3, List<AudioSegment> segments, long totalMs) {}

    class TtsException extends RuntimeException {
        public TtsException(String message) { super(message); }
        public TtsException(String message, Throwable cause) { super(message, cause); }
    }

    /** Builds timed segments from the per-segment durations reported by a synthesizer. */
    static SynthesizedAudio assemble(byte[] mp3, List<SentenceSegmenter.Segment> segments, List<Long> durationsMs) {
        if (durationsMs.size() != segments.size()) throw new TtsException("segment count mismatch");
        var timed = new java.util.ArrayList<AudioSegment>();
        long at = 0;
        for (int i = 0; i < segments.size(); i++) {
            long duration = durationsMs.get(i);
            if (duration < 1) throw new TtsException("segment duration must be positive");
            timed.add(new AudioSegment(at, at + duration, segments.get(i).charStart(), segments.get(i).charEnd()));
            at += duration;
        }
        return new SynthesizedAudio(mp3, List.copyOf(timed), at);
    }
}
