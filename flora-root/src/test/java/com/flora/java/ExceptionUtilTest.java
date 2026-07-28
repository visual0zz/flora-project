package com.flora.java;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExceptionUtil 异常处理工具类的单元测试。
 * 覆盖 wrap、getRootCause、getMessage 及 isCausedBy。
 */
class ExceptionUtilTest {

    // ==================== 包装 ====================

    @Test
    void wrapWithMessage() {
        IOException cause = new IOException("disk full");
        RuntimeException wrapped = ExceptionUtil.wrap(cause, "operation failed");
        assertEquals("operation failed", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
    }

    @Test
    void wrapWithoutMessageUsesCauseMessage() {
        IOException cause = new IOException("disk full");
        RuntimeException wrapped = ExceptionUtil.wrap(cause);
        assertEquals("disk full", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
    }

    @Test
    void wrapRejectsNullCause() {
        assertThrows(NullPointerException.class, () -> ExceptionUtil.wrap(null, "x"));
    }

    // ==================== 根因 ====================

    @Test
    void getRootCauseReturnsDeepest() {
        Throwable root = new IllegalStateException("root");
        Throwable mid = new RuntimeException("mid", root);
        Throwable top = new RuntimeException("top", mid);
        assertSame(root, ExceptionUtil.getRootCause(top));
    }

    @Test
    void getRootCauseReturnsSelfWhenNoCause() {
        Throwable self = new RuntimeException("self");
        assertSame(self, ExceptionUtil.getRootCause(self));
    }

    @Test
    void getRootCauseNullSafe() {
        assertNull(ExceptionUtil.getRootCause(null));
    }

    // ==================== 安全取消息 ====================

    @Test
    void getMessageNullSafe() {
        assertEquals("boom", ExceptionUtil.getMessage(new RuntimeException("boom")));
        assertNull(ExceptionUtil.getMessage(null));
    }

    // ==================== 因果链匹配 ====================

    @Test
    void isCausedByMatchesInChain() {
        IOException root = new IOException("io");
        RuntimeException top = new RuntimeException("wrap", root);
        assertTrue(ExceptionUtil.isCausedBy(top, IOException.class));
        assertTrue(ExceptionUtil.isCausedBy(top, RuntimeException.class));
        assertFalse(ExceptionUtil.isCausedBy(top, IllegalStateException.class));
    }

    @Test
    void isCausedByNullSafe() {
        assertFalse(ExceptionUtil.isCausedBy(null, IOException.class));
        assertThrows(NullPointerException.class,
                () -> ExceptionUtil.isCausedBy(new RuntimeException("x"), null));
    }
}
