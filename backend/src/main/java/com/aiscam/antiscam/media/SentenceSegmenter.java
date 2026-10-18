package com.aiscam.antiscam.media;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a line after sentence-ending and comma-like punctuation (segmentation_version 1). Indexes are Unicode
 * code points; the segments cover [0, codePointCount) without gaps, and punctuation stays with the preceding text.
 */
public final class SentenceSegmenter {
    public static final String VERSION = "1";
    private static final String BREAKS = "。！？!?；;…，,、：:";
    private SentenceSegmenter() {}

    public static List<Segment> split(String content) {
        int[] cps = content.codePoints().toArray();
        List<Segment> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < cps.length; i++) {
            boolean isBreak = BREAKS.indexOf(cps[i]) >= 0;
            boolean nextIsBreak = i + 1 < cps.length && BREAKS.indexOf(cps[i + 1]) >= 0;
            if (isBreak && !nextIsBreak) {
                add(result, cps, start, i + 1);
                start = i + 1;
            }
        }
        if (start < cps.length) add(result, cps, start, cps.length);
        return result;
    }

    private static void add(List<Segment> result, int[] cps, int start, int end) {
        String text = new String(cps, start, end - start);
        boolean onlyPunctuation = text.codePoints().allMatch(c -> BREAKS.indexOf(c) >= 0 || Character.isWhitespace(c));
        if (onlyPunctuation && !result.isEmpty()) {
            var last = result.removeLast();
            result.add(new Segment(last.text() + text, last.charStart(), end));
        } else {
            result.add(new Segment(text, start, end));
        }
    }

    public record Segment(String text, int charStart, int charEnd) {}
}
