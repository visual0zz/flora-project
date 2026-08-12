package com.flora.root.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionUtilTest {

    // ====== wrap ======

    @Test
    void wrapWithMessage() {
        var cause = new IllegalArgumentException("bad arg");
        var ex = ExceptionUtil.wrap(cause, "wrapped");
        assertInstanceOf(RuntimeException.class, ex);
        assertEquals("wrapped", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void wrapWithoutMessage() {
        var cause = new IllegalArgumentException("bad arg");
        var ex = ExceptionUtil.wrap(cause);
        assertEquals("bad arg", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void wrapNullCauseThrows() {
        assertThrows(NullPointerException.class,
                () -> ExceptionUtil.wrap(null, "msg"));
        assertThrows(NullPointerException.class,
                () -> ExceptionUtil.wrap(null));
    }

    // ====== getRootCause ======

    @Test
    void getRootCauseOfNullReturnsNull() {
        assertNull(ExceptionUtil.getRootCause(null));
    }

    @Test
    void getRootCauseOfPlainException() {
        var ex = new RuntimeException("plain");
        assertSame(ex, ExceptionUtil.getRootCause(ex));
    }

    @Test
    void getRootCauseOfChainedExceptions() {
        var root = new IllegalArgumentException("root");
        var mid = new RuntimeException("mid", root);
        var top = new Exception("top", mid);
        assertSame(root, ExceptionUtil.getRootCause(top));
    }

    @Test
    void getRootCauseDetectsCycle() {
        var a = new RuntimeException("a");
        var b = new RuntimeException("b", a);
        // 触发循环：a 的 cause 指向 b（注意 initCause 可以设置）
        a.initCause(b);
        assertNotNull(ExceptionUtil.getRootCause(a));
        // 不应无限循环
    }

    // ====== getMessage ======

    @Test
    void getMessageNullSafe() {
        assertNull(ExceptionUtil.getMessage(null));
    }

    @Test
    void getMessageReturnsMessage() {
        assertEquals("err", ExceptionUtil.getMessage(new Exception("err")));
    }

    @Test
    void getMessageOfExceptionWithoutMessage() {
        assertNull(ExceptionUtil.getMessage(new Exception()));
    }

    // ====== isCausedBy ======

    @Test
    void isCausedByDirectMatch() {
        var ex = new IllegalArgumentException("bad");
        assertTrue(ExceptionUtil.isCausedBy(ex, IllegalArgumentException.class));
        assertFalse(ExceptionUtil.isCausedBy(ex, IllegalStateException.class));
    }

    @Test
    void isCausedByInChain() {
        var root = new IllegalArgumentException("root");
        var top = new RuntimeException("top", root);
        assertTrue(ExceptionUtil.isCausedBy(top, IllegalArgumentException.class));
    }

    @Test
    void isCausedByNullThrowableReturnsFalse() {
        assertFalse(ExceptionUtil.isCausedBy(null, RuntimeException.class));
    }

    @Test
    void isCausedByNullTypeThrows() {
        assertThrows(NullPointerException.class,
                () -> ExceptionUtil.isCausedBy(new Exception(), null));
    }

    @Test
    void isCausedByDetectsCycle() {
        var a = new RuntimeException("a");
        var b = new RuntimeException("b", a);
        a.initCause(b);
        // 不应无限循环，且应找到匹配
        assertTrue(ExceptionUtil.isCausedBy(a, RuntimeException.class));
    }
}
