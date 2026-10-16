package com.aiscam.antiscam.content.avatar;

import com.aiscam.antiscam.common.error.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AvatarStore {
    private final Path directory;

    public AvatarStore(@Value("${app.content.avatar-store:var}") Path directory) {
        // Support both repository-root and backend working directories for local launches.
        if (directory.equals(Path.of("var")) && !Files.isDirectory(directory)
                && Files.isDirectory(Path.of("backend/var"))) directory = Path.of("backend/var");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public AvatarResource load(String key) {
        if (!AvatarKey.isValid(key)) throw ApiException.field("key", "VALUE_INVALID", "头像标识格式不正确");
        try {
            Path root = directory.toRealPath();
            Path file = root.resolve(key).toRealPath();
            if (!file.startsWith(root) || Files.size(file) > 5 * 1024 * 1024) throw ApiException.notFound();
            byte[] bytes = Files.readAllBytes(file);
            boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                    new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            boolean jpg = bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255;
            if (!(key.endsWith(".png") && png || key.endsWith(".jpg") && jpg)
                    || ImageIO.read(new ByteArrayInputStream(bytes)) == null
                    || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(AvatarKey.publicKey(key))) {
                throw ApiException.notFound();
            }
            return new AvatarResource(bytes, png ? "image/png" : "image/jpeg");
        } catch (IOException e) {
            throw ApiException.notFound();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
