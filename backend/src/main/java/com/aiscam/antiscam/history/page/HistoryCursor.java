package com.aiscam.antiscam.history.page;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.common.web.SignedCursor;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class HistoryCursor {
    private static final String SCOPE = "HISTORY|startedAtDesc,idDesc";
    private final SignedCursor signed = new SignedCursor();

    public String encode(String account, Instant startedAt, long id) {
        if (startedAt.toEpochMilli() < 0 || id < 1) throw ApiException.invalidCursor();
        return signed.encode(SCOPE, account, startedAt.toEpochMilli() + ":" + id);
    }

    public Keyset decode(String account, String cursor) {
        var value = signed.decode(SCOPE, account, cursor);
        if (value.isEmpty()) return null;
        try {
            String payload = value.get();
            if (!payload.matches("[0-9]{1,19}:[1-9][0-9]{0,18}")) throw ApiException.invalidCursor();
            String[] parts = payload.split(":");
            return new Keyset(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException e) {
            throw ApiException.invalidCursor();
        }
    }

    public record Keyset(Instant startedAt, long id) {}
}
