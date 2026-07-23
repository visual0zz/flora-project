package com.flora.algebra;

import com.flora.algebra.prime.PrimeLongCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrimeLongCalculator 的单元测试（long 范围）。
 * 测试 long 范围内素数判断和下一个素数查找，覆盖大素数及边界值。
 */
class PrimeLongCalculatorTest {

    /**
     * 测试 isPrime 方法：覆盖偶数排除、负数、小素数、大素数（含 Mersenne 素数）、合数及 Long.MAX_VALUE。
     */
    @Test
    void isPrime() {
        
        for (long n = 4; n <= 10000; n += 2) {
            assertFalse(PrimeLongCalculator.isPrime(n),
                    ": " + n + " is even (>2), must be composite");
        }

        
        assertFalse(PrimeLongCalculator.isPrime(-1));
        assertFalse(PrimeLongCalculator.isPrime(-2));
        assertFalse(PrimeLongCalculator.isPrime(-10000));
        assertFalse(PrimeLongCalculator.isPrime(Long.MIN_VALUE));

        
        assertTrue(PrimeLongCalculator.isPrime(2));
        assertTrue(PrimeLongCalculator.isPrime(3));
        assertTrue(PrimeLongCalculator.isPrime(5));
        assertTrue(PrimeLongCalculator.isPrime(7));
        assertTrue(PrimeLongCalculator.isPrime(11));
        assertTrue(PrimeLongCalculator.isPrime(13));
        assertTrue(PrimeLongCalculator.isPrime(17));
        assertTrue(PrimeLongCalculator.isPrime(19));
        assertTrue(PrimeLongCalculator.isPrime(37));
        assertTrue(PrimeLongCalculator.isPrime(46349));
        assertTrue(PrimeLongCalculator.isPrime(9973));
        assertTrue(PrimeLongCalculator.isPrime(104729));
        assertTrue(PrimeLongCalculator.isPrime(104743));
        assertTrue(PrimeLongCalculator.isPrime(999999937));
        assertTrue(PrimeLongCalculator.isPrime(100000007));

        
        assertTrue(PrimeLongCalculator.isPrime(2147483647L));           
        assertTrue(PrimeLongCalculator.isPrime(2305843009213693951L));  
        assertTrue(PrimeLongCalculator.isPrime(9223372036854775783L));  

        
        assertFalse(PrimeLongCalculator.isPrime(0));
        assertFalse(PrimeLongCalculator.isPrime(1));
        assertFalse(PrimeLongCalculator.isPrime(9));
        assertFalse(PrimeLongCalculator.isPrime(15));
        assertFalse(PrimeLongCalculator.isPrime(100));
        assertFalse(PrimeLongCalculator.isPrime(9975));
        assertFalse(PrimeLongCalculator.isPrime(561));   
        assertFalse(PrimeLongCalculator.isPrime(1105));  
        assertFalse(PrimeLongCalculator.isPrime(1729));  

        
        assertFalse(PrimeLongCalculator.isPrime(Long.MAX_VALUE));        
        assertFalse(PrimeLongCalculator.isPrime(9223372036854775807L));  
    }

    /**
     * 测试 nextPrime 方法：验证 long 范围内下一个素数查找的正确性及溢出异常。
     */
    @Test
    void nextPrime() {
        
        for (long n = 1; n <= 10000; n++) {
            long np = PrimeLongCalculator.nextPrime(n);
            assertTrue(np > n,
                    ": nextPrime(" + n + ")=" + np + " must be > " + n);
            assertTrue(PrimeLongCalculator.isPrime(np),
                    ": nextPrime(" + n + ")=" + np + " should be prime");
        }

        
        assertEquals(2, PrimeLongCalculator.nextPrime(0));
        assertEquals(2, PrimeLongCalculator.nextPrime(1));
        assertEquals(3, PrimeLongCalculator.nextPrime(2));
        assertEquals(5, PrimeLongCalculator.nextPrime(3));
        assertEquals(5, PrimeLongCalculator.nextPrime(4));
        assertEquals(7, PrimeLongCalculator.nextPrime(5));
        assertEquals(7, PrimeLongCalculator.nextPrime(6));
        assertEquals(11, PrimeLongCalculator.nextPrime(8));
        assertEquals(13, PrimeLongCalculator.nextPrime(12));
        assertEquals(17, PrimeLongCalculator.nextPrime(13));
        assertEquals(19, PrimeLongCalculator.nextPrime(17));
        assertEquals(137, PrimeLongCalculator.nextPrime(135));
        assertEquals(11111117, PrimeLongCalculator.nextPrime(11111111));
        assertEquals(100000007, PrimeLongCalculator.nextPrime(100_000_000));

        
        assertEquals(2147483659L, PrimeLongCalculator.nextPrime(2147483647L));
        assertEquals(2305843009213693967L, PrimeLongCalculator.nextPrime(2305843009213693951L));

        
        assertThrows(ArithmeticException.class, () -> PrimeLongCalculator.nextPrime(Long.MAX_VALUE - 1));
    }

}
