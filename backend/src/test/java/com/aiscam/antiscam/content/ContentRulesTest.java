package com.aiscam.antiscam.content;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.avatar.*;
import com.aiscam.antiscam.content.page.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.*;

class ContentRulesTest {
    @Test
    void cursorIsSignedAndBoundToAccountAndResource() {
        var codec = new ContentCursor();
        String token = codec.encode(ContentCursor.SCOPE_SCAMMERS, "user1", 11);
        assertEquals(11, codec.decode(ContentCursor.SCOPE_SCAMMERS, "user1", token));
        assertEquals("INVALID_CURSOR", assertThrows(ApiException.class,
                () -> codec.decode(ContentCursor.SCOPE_VICTIMS, "user1", token)).code);
        assertThrows(ApiException.class, () -> codec.decode(ContentCursor.SCOPE_SCAMMERS, "user2", token));
        byte[] modified = Base64.getUrlDecoder().decode(token);
        modified[0] = '2';
        assertThrows(ApiException.class, () -> codec.decode(ContentCursor.SCOPE_SCAMMERS, "user1",
                Base64.getUrlEncoder().withoutPadding().encodeToString(modified)));
        assertEquals(0, codec.decode(ContentCursor.SCOPE_SCAMMERS, "user1", ""));
    }

    @Test
    void validatesQueryAndIdsWithoutOverflow() {
        var query = new LinkedMultiValueMap<String, String>();
        assertEquals(20, PageQuery.from(query).limit());
        for (String limit : new String[]{"0", "101", "abc", "1.5", "999999999999999999999"}) {
            query.set("limit", limit);
            assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class, () -> PageQuery.from(query)).code);
        }
        query.clear(); query.set("enabled", "false");
        assertThrows(ApiException.class, () -> PageQuery.from(query));
        assertEquals(400, assertThrows(ApiException.class, () -> PageQuery.id("007")).status);
        assertEquals(404, assertThrows(ApiException.class, () -> PageQuery.id("99999999999999999999")).status);
    }

    @Test
    void allSeedAvatarsAreDecodableAndContentAddressed() throws Exception {
        var store = new AvatarStore(Path.of("var"));
        try (var files = Files.list(Path.of("var/avatars"))) {
            var avatars = files.toList();
            // Five seed profiles x two portrait frames (closed mouth / open mouth, V1.1).
            assertEquals(10, avatars.size());
            for (var file : avatars) {
                String key = "avatars/" + file.getFileName();
                assertTrue(AvatarKey.isValid(key));
                assertEquals(64, AvatarKey.publicKey(key).length());
                assertEquals("image/png", store.load(key).mediaType());
            }
        }
        assertEquals(store.dimensions("avatars/3e1d7c5636e6bafa6dd598a71ebb482c086430ae55b03d88a31049de2421becc.png"),
                store.dimensions("avatars/f6251f3b667ff5ab944425676b3ff8da2743d76e902990fa9ad42d7e453a734b.png"));
        assertFalse(AvatarKey.isValid("avatars/../../application.yml"));
        assertThrows(ApiException.class, () -> store.load("avatars/../../application.yml"));
        assertEquals(404, assertThrows(ApiException.class, () -> store.load("avatars/" + "0".repeat(64) + ".png")).status);
    }
}
