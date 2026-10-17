package com.aiscam.antiscam.admin;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.common.web.RequestContent;
import com.aiscam.antiscam.content.avatar.AvatarStore;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AdminRulesTest {
    @TempDir Path directory;

    @Test
    void textCapacityUsesUnicodeCodePoints() {
        RequestContent.checkField("name", "😀".repeat(64), 64);
        assertEquals("VALIDATION_FAILED", assertThrows(ApiException.class,
                () -> RequestContent.checkField("name", "😀".repeat(65), 64)).code);
        RequestContent.checkField("description", "字".repeat(16000), RequestContent.MAX_TEXT_CHARS);
        assertThrows(ApiException.class, () -> RequestContent.checkField("description", "字".repeat(16001), RequestContent.MAX_TEXT_CHARS));
        assertThrows(ApiException.class, () -> RequestContent.checkField("name", " ", 64));
    }

    @Test
    void uploadsAreImmutableAndRejectInvalidAnimatedAndOversizedImages() throws Exception {
        byte[] png;
        try (var paths = Files.list(Path.of("var/avatars"))) { png = Files.readAllBytes(paths.findFirst().orElseThrow()); }
        var store = new AvatarStore(directory);
        String key = store.store(png);
        assertEquals(key, store.store(png));
        assertArrayEquals(png, store.load(key).bytes());
        try (var paths = Files.list(directory.resolve("avatars"))) { assertEquals(1, paths.count()); }
        assertEquals("INVALID_IMAGE", assertThrows(ApiException.class, () -> store.store(new byte[0])).code);
        assertEquals("INVALID_IMAGE", assertThrows(ApiException.class, () -> store.store(Arrays.copyOf(png, 8))).code);
        assertEquals("FILE_TOO_LARGE", assertThrows(ApiException.class, () -> store.store(new byte[5 * 1024 * 1024 + 1])).code);
        // APNG animation control follows IHDR, before image data.
        byte[] animated = new byte[png.length + 20];
        System.arraycopy(png, 0, animated, 0, 33);
        ByteBuffer.wrap(animated, 33, 4).putInt(8);
        System.arraycopy(new byte[]{97, 99, 84, 76}, 0, animated, 37, 4);
        System.arraycopy(png, 33, animated, 53, png.length - 33);
        assertEquals("INVALID_IMAGE", assertThrows(ApiException.class, () -> store.store(animated)).code);
    }
}
