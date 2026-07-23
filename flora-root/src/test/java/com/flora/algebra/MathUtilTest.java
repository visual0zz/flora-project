package com.flora.algebra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MathUtil 工具类的单元测试。
 * 测试素数判断、前后素数查找及素数计数功能。
 */
class MathUtilTest {

    /**
     * 测试素数判断、nextPrime/prevPrime、primeCount 及 primeOf 方法。
     * 覆盖 int 和 long 两种重载。
     */
    @Test
    void prime() {
        assertTrue(MathUtil.isPrime(2));
        assertTrue(MathUtil.isPrime(2L));
        assertFalse(MathUtil.isPrime(10));
        assertFalse(MathUtil.isPrime(10L));

        assertEquals(2,MathUtil.nextPrime(1));
        assertEquals(3,MathUtil.nextPrime(2));
        assertEquals(5,MathUtil.nextPrime(3));
        assertEquals(10007,MathUtil.nextPrime(10000));
        assertEquals(2,MathUtil.prevPrime(3));
        assertEquals(3,MathUtil.prevPrime(4));
        assertEquals(3,MathUtil.prevPrime(5));
        assertEquals(10007,MathUtil.prevPrime(10009));
        assertEquals(100000007,MathUtil.prevPrime(100000020));

        assertEquals(2,MathUtil.nextPrime(1L));
        assertEquals(3,MathUtil.nextPrime(2L));
        assertEquals(5,MathUtil.nextPrime(3L));
        assertEquals(10007,MathUtil.nextPrime(10000L));
        assertEquals(2,MathUtil.prevPrime(3L));
        assertEquals(3,MathUtil.prevPrime(4L));
        assertEquals(3,MathUtil.prevPrime(5L));
        assertEquals(10009,MathUtil.prevPrime(10015L));


        assertEquals(8,MathUtil.primeCount(20));

        assertEquals(8,MathUtil.primeCount(20,1));
        assertEquals(8,MathUtil.primeCount(1,20));
        assertEquals(2,MathUtil.primeCount(1,3));
        assertEquals(4,MathUtil.primeCount(5,14));
        assertEquals(6,MathUtil.primeCount(-100,14));
        assertEquals(0,MathUtil.primeCount(-100,-14));
        assertEquals(6,MathUtil.primeCount(100000000,100000090));
        assertEquals(5411,MathUtil.primeCount(100000000,100100000));
        assertEquals(4814936,MathUtil.primeCount(1000000000,1100000000));

        assertEquals(2,MathUtil.primeOf(1));

        assertThrows(IllegalArgumentException.class,()->MathUtil.prevPrime(2));
        assertThrows(IllegalArgumentException.class,()->MathUtil.prevPrime(2L));
    }
}