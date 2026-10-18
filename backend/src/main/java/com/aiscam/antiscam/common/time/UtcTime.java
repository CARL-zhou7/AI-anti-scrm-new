package com.aiscam.antiscam.common.time;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/** UTC wire format with exactly three millisecond digits and a trailing Z (UtcMillis). */
public final class UtcTime {
    private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private UtcTime() {}

    public static String format(Instant instant) { return instant == null ? null : FORMAT.format(instant); }
}
