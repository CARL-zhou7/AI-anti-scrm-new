package com.aiscam.antiscam.content.avatar;

import com.aiscam.antiscam.common.error.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class AvatarImage {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private AvatarImage() {}

    static String extension(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw ApiException.fileTooLarge();
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        boolean jpg = bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255;
        if (!png && !jpg) throw ApiException.imageInvalid();
        if (png) {
            int offset = 8;
            boolean ended = false;
            while (offset + 12 <= bytes.length) {
                long length = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4).getInt());
                if (length > bytes.length - offset - 12) throw ApiException.imageInvalid();
                String chunk = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
                if (chunk.equals("acTL")) throw ApiException.imageInvalid();
                offset += (int) length + 12;
                if (chunk.equals("IEND")) { ended = true; break; }
            }
            if (!ended) throw ApiException.imageInvalid();
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ApiException.imageInvalid();
            var reader = readers.next();
            try {
                reader.setInput(input);
                // Bound decoded memory as well as compressed bytes to prevent image decompression exhaustion.
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000
                        || reader.getNumImages(true) != 1 || reader.read(0) == null) throw ApiException.imageInvalid();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException e) {
            throw ApiException.imageInvalid();
        }
        return png ? "png" : "jpg";
    }
}
