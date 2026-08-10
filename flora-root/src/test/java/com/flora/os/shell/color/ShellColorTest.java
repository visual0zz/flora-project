package com.flora.os.shell.color;

import com.flora.os.OsUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 跨平台 ANSI 颜色与样式工具测试：枚举包装、Style 构建器组合、平台探测与 VT 初始化标志。
 */
class ShellColorTest {

    @Test
    void shellColorWrapAddsForegroundAndReset() {
        assertEquals("\u001B[31mred\u001B[0m", ShellColor.RED.wrap("red"));
        assertEquals("\u001B[36msky\u001B[0m", ShellColor.SKY.wrap("sky"));
    }

    @Test
    void shellBackgroundColorWrapAddsBackgroundAndReset() {
        assertEquals("\u001B[41mhi\u001B[0m", ShellBackgroundColor.RED.wrap("hi"));
    }

    @Test
    void styleCombinesForegroundBackgroundAndStyles() {
        String s = Style.of(ShellColor.RED)
                .on(ShellBackgroundColor.WHITE)
                .with(ShellStyle.BOLD)
                .wrap("alert");
        assertEquals("\u001B[31;47;1malert\u001B[0m", s);
    }

    @Test
    void styleForegroundOnlyEqualsColorWrap() {
        assertEquals(ShellColor.GREEN.wrap("x"), Style.of(ShellColor.GREEN).wrap("x"));
    }

    @Test
    void emptyStyleReturnsOriginalText() {
        // 无前景/背景/样式时不应插入任何转义序列
        assertEquals("plain", Style.empty().wrap("plain"));
    }

    @Test
    void ensureVirtualTerminalIsIdempotent() {
        AnsiConsole.resetForTesting();
        assertFalse(AnsiConsole.isInitialized());
        AnsiConsole.ensureVirtualTerminal();
        assertTrue(AnsiConsole.isInitialized());
        // 二次调用不应抛异常且保持已初始化
        AnsiConsole.ensureVirtualTerminal();
        assertTrue(AnsiConsole.isInitialized());
    }

    @Test
    void osUtilDetectsPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            assertTrue(OsUtil.isWindows());
            assertFalse(OsUtil.isLinux());
        } else if (os.contains("linux")) {
            assertTrue(OsUtil.isLinux());
            assertFalse(OsUtil.isWindows());
        } else if (os.contains("mac")) {
            assertTrue(OsUtil.isMac());
        }
    }
}
