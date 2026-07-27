package com.flora.java;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DataUtilTest {

    // ── ClassUtil.getAllInterfaces: 传递父接口必须被收集 ──

    @Test
    void getAllInterfacesCollectsTransitiveSuperInterfaces() {
        Set<Class<?>> ifaces = ClassUtil.getAllInterfaces(ArrayList.class);
        // 直接声明的接口
        assertTrue(ifaces.contains(List.class));
        assertTrue(ifaces.contains(java.util.RandomAccess.class));
        assertTrue(ifaces.contains(Cloneable.class));
        assertTrue(ifaces.contains(java.io.Serializable.class));
        // 传递父接口（修复前会丢失）
        assertTrue(ifaces.contains(Collection.class), "应收集到 Collection（List 的父接口）");
        assertTrue(ifaces.contains(Iterable.class), "应收集到 Iterable（Collection 的父接口）");
    }

    @Test
    void getAllInterfacesNullThrows() {
        assertThrows(NullPointerException.class, () -> ClassUtil.getAllInterfaces(null));
    }

    // ── ClassUtil.loadClass: 应使用线程上下文类加载器 ──

    @Test
    void loadClassResolvesExistingClass() {
        assertEquals(String.class, ClassUtil.loadClass("java.lang.String"));
    }

    @Test
    void loadClassNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> ClassUtil.loadClass(null));
    }

    // ── ClassUtil 判定方法 null 守卫 ──

    @Test
    void predicateMethodsNullGuard() {
        assertThrows(NullPointerException.class, () -> ClassUtil.isAbstract(null));
        assertThrows(NullPointerException.class, () -> ClassUtil.isInterface(null));
        assertThrows(NullPointerException.class, () -> ClassUtil.isPrimitive(null));
    }

    // ── StrUtil.substring: 负下标应一次性钳位，不得递归栈溢出 ──

    @Test
    void substringNegativeClampsToStart() {
        assertEquals("lo", StrUtil.substring("hello", -2));
        assertEquals("hello", StrUtil.substring("hello", -100));
        assertEquals("llo", StrUtil.substring("hello", 2));
        assertEquals("", StrUtil.substring("hello", 10));
    }

    @Test
    void substringExtremeNegativeDoesNotOverflow() {
        // 修复前 Integer.MIN_VALUE 会触发深递归 StackOverflowError
        assertEquals("hello", StrUtil.substring("hello", Integer.MIN_VALUE));
    }

    // ── BytesUtil: 异常类型与 concat 别名 ──

    @Test
    void bytesUtilThrowsIllegalArgumentExceptionForNull() {
        assertThrows(IllegalArgumentException.class, () -> BytesUtil.bytes2int(null));
        assertThrows(IllegalArgumentException.class, () -> BytesUtil.concat(null, new byte[1]));
    }

    @Test
    void concatReturnsDefensiveCopyWhenEmpty() {
        byte[] a = {1, 2};
        byte[] b = {3, 4};
        byte[] merged = BytesUtil.concat(a, b);
        assertNotSame(a, merged);
        assertNotSame(b, merged);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, merged);

        // 一侧为空时返回的是副本，而非入参引用别名
        byte[] empty = {};
        byte[] onlyA = BytesUtil.concat(a, empty);
        assertNotSame(a, onlyA);
        assertArrayEquals(a, onlyA);

        byte[] onlyB = BytesUtil.concat(empty, b);
        assertNotSame(b, onlyB);
        assertArrayEquals(b, onlyB);
    }

    @Test
    void primitiveRoundTrip() {
        assertEquals(0x12345678, BytesUtil.bytes2int(BytesUtil.int2bytes(0x12345678)));
        assertEquals(1.25f, BytesUtil.bytes2float(BytesUtil.float2bytes(1.25f)));
    }
}
