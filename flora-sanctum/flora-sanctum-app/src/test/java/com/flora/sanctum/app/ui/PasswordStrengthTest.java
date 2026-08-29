package com.flora.sanctum.app.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordStrengthTest {

    @Test
    void emptyPasswordIsBad() {
        PasswordStrength s = PasswordStrength.evaluate("", null);
        assertEquals(PasswordStrength.Quality.BAD, s.quality());
        assertEquals(0.0, s.entropy(), 0.001);
    }

    @Test
    void commonPasswordIsWeakOrPoor() {
        // "password" 是 zxcvbn 词典中的高频弱密码，熵应远低于 75
        PasswordStrength s = PasswordStrength.evaluate("password", null);
        assertTrue(s.entropy() < 75, "common password entropy should be low, got " + s.entropy());
        assertTrue(s.quality() == PasswordStrength.Quality.BAD
                || s.quality() == PasswordStrength.Quality.POOR
                || s.quality() == PasswordStrength.Quality.WEAK);
        assertFalse(s.warning().isEmpty(), "weak password should carry a warning");
    }

    @Test
    @SuppressWarnings("osmetes:secret")
    void longRandomPassphraseIsExcellent() {
        // 高熵随机串，应达到优（熵 >= 100）
        String strong = "9K#vL2$mQpXr8@WnZc7T!bH4&JfY6uE1";
        PasswordStrength s = PasswordStrength.evaluate(strong, null);
        assertEquals(PasswordStrength.Quality.EXCELLENT, s.quality(),
                "strong random password should be excellent, entropy=" + s.entropy());
        assertTrue(s.entropy() >= 100);
    }

    @Test
    @SuppressWarnings("osmetes:secret")
    void userWordsLowerStrength() {
        // 含用户弱词（仓库名）时，强度应被拉低
        PasswordStrength plain = PasswordStrength.evaluate("FloraProject2024", null);
        PasswordStrength withUser = PasswordStrength.evaluate("FloraProject2024",
                java.util.List.of("FloraProject"));
        assertTrue(withUser.entropy() <= plain.entropy() + 1e-9,
                "supplying user words must not increase entropy");
    }
}
