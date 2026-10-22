package com.aiscam.antiscam.content;
import com.aiscam.antiscam.content.avatar.AvatarStore;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ElderlyMaleAvatarTest {
    @Test void newPortraitPairIsDecodableContentAddressedAndKeepsOldPortraitAvailable() throws Exception {
        var store=new AvatarStore(Path.of("var"));
        var closed=ImageIO.read(new ByteArrayInputStream(store.load("avatars/2f7dcbc559d76bfaa1646c04f44447c8dcff939f775bf1634293675789b81e93.png").bytes()));
        var speaking=ImageIO.read(new ByteArrayInputStream(store.load("avatars/9d02017b4f30bfbbfc4d7cb2563fa55cf8ecce249f95ec72d223736de7a0bd59.png").bytes()));
        assertEquals(512,closed.getWidth());assertEquals(512,closed.getHeight());
        assertEquals(closed.getWidth(),speaking.getWidth());assertNotEquals(closed.getRGB(255,267),speaking.getRGB(255,267));
        assertNotNull(store.load("avatars/36b4e45ddf67bffe1bdfb7cfb9f8c47a5166e25dc86fb2ddab42cc46b66f358b.png"));
    }
}
