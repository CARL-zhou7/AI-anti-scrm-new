package com.aiscam.antiscam.content.avatar;

import com.aiscam.antiscam.common.error.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.awt.Dimension;
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
        if (directory.equals(Path.of("var")) && !Files.isDirectory(directory.resolve("avatars"))
                && Files.isDirectory(Path.of("backend/var/avatars"))) directory = Path.of("backend/var");
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

    /** Pixel size of a verified stored avatar (used to check the closed/open-mouth frames match). */
    public Dimension dimensions(String storedKey) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(load(storedKey).bytes()));
            return new Dimension(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw ApiException.notFound();
        }
    }

    public String storedKey(String publicKey) {
        AvatarKey.validatePublicKey(publicKey);
        if (!publicKey.matches("[0-9a-f]{64}")) throw ApiException.notFound();
        for (String extension : new String[]{"png", "jpg"}) {
            String key = "avatars/" + publicKey + "." + extension;
            try { load(key); return key; }
            catch (ApiException e) { if (e.status != 404) throw e; }
        }
        throw ApiException.notFound();
    }

    public synchronized String store(byte[] bytes) {
        String extension = AvatarImage.extension(bytes);
        Path temporary = null;
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String key = "avatars/" + hash + "." + extension;
            Files.createDirectories(directory);
            Path root = directory.toRealPath();
            Path folder = root.resolve("avatars");
            Files.createDirectories(folder);
            folder = folder.toRealPath();
            if (!folder.startsWith(root)) throw new IOException();
            Path target = folder.resolve(hash + "." + extension);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Arrays.equals(load(key).bytes(), bytes)) throw new IOException();
                return key;
            }
            temporary = Files.createTempFile(folder, ".upload-", ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            load(key);
            return key;
        } catch (IOException e) {
            throw new ApiException(503, "SERVICE_UNAVAILABLE", "头像暂时无法保存，请稍后重试");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* No published avatar is deleted. */ }
            }
        }
    }
}
