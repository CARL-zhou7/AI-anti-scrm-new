package com.aiscam.antiscam.common.web;

import com.aiscam.antiscam.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SignedCursor {
    private final byte[] secret = new byte[32];

    public SignedCursor() {
        // shortcut: restart invalidates cursors; share a persistent key when deploying multiple instances.
        new SecureRandom().nextBytes(secret);
    }

    public String encode(String scope, String account, String value) {
        byte[] payload = value.getBytes(StandardCharsets.US_ASCII);
        byte[] result = Arrays.copyOf(payload, payload.length + 32);
        System.arraycopy(sign(scope, account, payload), 0, result, payload.length, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
    }

    public Optional<String> decode(String scope, String account, String cursor) {
        if (cursor == null || cursor.isEmpty()) return Optional.empty();
        try {
            if (!cursor.matches("[A-Za-z0-9_-]{1,256}")) throw ApiException.invalidCursor();
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            if (bytes.length <= 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(cursor)) {
                throw ApiException.invalidCursor();
            }
            byte[] payload = Arrays.copyOf(bytes, bytes.length - 32);
            if (!MessageDigest.isEqual(sign(scope, account, payload), Arrays.copyOfRange(bytes, payload.length, bytes.length))) {
                throw ApiException.invalidCursor();
            }
            return Optional.of(new String(payload, StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException e) {
            throw ApiException.invalidCursor();
        }
    }

    private byte[] sign(String scope, String account, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update((scope + "\0" + account + "\0").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cursor signing unavailable", e);
        }
    }
}
