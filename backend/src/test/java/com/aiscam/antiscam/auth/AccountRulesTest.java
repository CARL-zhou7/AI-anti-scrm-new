package com.aiscam.antiscam.auth;

import com.aiscam.antiscam.auth.username.PasswordPolicy;
import com.aiscam.antiscam.auth.username.Username;
import com.aiscam.antiscam.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class AccountRulesTest {
    @Test
    void usernameRules() {
        assertEquals("李雷", Username.normalize(" 李雷 "));
        for (String valid : new String[]{"李雷", "ab", "abc_123", "中".repeat(24)}) {
            assertDoesNotThrow(() -> Username.validate(valid));
        }
        for (String invalid : new String[]{"a", "中".repeat(25), "张 三", "user%", "ＡＢＣ", "user😀"}) {
            assertThrows(ApiException.class, () -> Username.validate(invalid));
        }
        assertEquals(Username.key("ZhangSan"), Username.key("zhangsan"));
    }

    @Test
    void passwordsPreserveSpacesWeakValuesAndUnicode() {
        for (String value : new String[]{"123456", " abc123 ", "😀".repeat(24)}) {
            assertDoesNotThrow(() -> PasswordPolicy.validate(value, value));
        }
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("12345"));
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("中".repeat(25)));
        assertThrows(ApiException.class, () -> PasswordPolicy.validate("abc123", " abc123 "));
        var encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String longPassword = "😀".repeat(24);
        assertTrue(encoder.matches(longPassword, encoder.encode(longPassword)));
        String hash = encoder.encode(" abc123 ");
        assertTrue(encoder.matches(" abc123 ", hash));
        assertFalse(encoder.matches("abc123", hash));
    }
}
