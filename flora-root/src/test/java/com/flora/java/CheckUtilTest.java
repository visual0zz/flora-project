package com.flora.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckUtilTest {

    // ── notNull ──

    @Test
    void notNullWithNonNullReturnsValue() {
        assertEquals("hello", CheckUtil.notNull("hello"));
        assertEquals(42, CheckUtil.notNull(42));
        assertNotNull(CheckUtil.notNull(new Object()));
    }

    @Test
    void notNullWithNullThrows() {
        assertThrows(NullPointerException.class, () -> CheckUtil.notNull(null));
    }

    @Test
    void notNullWithNullAndCustomMessage() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> CheckUtil.notNull(null, "自定义错误"));
        assertTrue(ex.getMessage().contains("自定义错误"));
    }

    // ── notEmpty ──

    @Test
    void notEmptyWithNonEmptyReturnsValue() {
        assertEquals("abc", CheckUtil.notEmpty("abc"));
    }

    @Test
    void notEmptyWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> CheckUtil.notEmpty(null));
    }

    @Test
    void notEmptyWithEmptyStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> CheckUtil.notEmpty(""));
    }

    // ── notBlank ──

    @Test
    void notBlankWithNonBlankReturnsValue() {
        assertEquals("x", CheckUtil.notBlank("x"));
    }

    @Test
    void notBlankWithBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> CheckUtil.notBlank("  "));
    }

    @Test
    void notBlankWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> CheckUtil.notBlank(null));
    }

    // ── mustTrue ──

    @Test
    void mustTrueWithTruePasses() {
        CheckUtil.mustTrue(true, "不应抛出");
    }

    @Test
    void mustTrueWithFalseThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> CheckUtil.mustTrue(false, "条件不满足"));
    }
}
