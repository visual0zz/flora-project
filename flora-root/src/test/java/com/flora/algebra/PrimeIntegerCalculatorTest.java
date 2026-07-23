package com.flora.algebra;

import com.flora.algebra.prime.PrimeIntegerCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrimeIntegerCalculator 的单元测试（int 范围）。
 * 测试素数判断、下一个素数查找、素数计数和第 n 个素数查询。
 */
class PrimeIntegerCalculatorTest {
    
    /**
     * 测试 isPrime 方法：覆盖边界值、小素数、合数及强伪素数。
     */
    @Test
    void isPrime() {
        
        assertFalse(PrimeIntegerCalculator.isPrime( 0));
        assertFalse(PrimeIntegerCalculator.isPrime( 1));
        assertFalse(PrimeIntegerCalculator.isPrime( -1));
        assertFalse(PrimeIntegerCalculator.isPrime( -2));
        assertFalse(PrimeIntegerCalculator.isPrime( -10000));
        assertFalse(PrimeIntegerCalculator.isPrime(-2147483648));
        assertTrue(PrimeIntegerCalculator.isPrime(2147483647));

        
        assertTrue(PrimeIntegerCalculator.isPrime( 2));
        assertTrue(PrimeIntegerCalculator.isPrime( 3));
        assertTrue(PrimeIntegerCalculator.isPrime( 5));
        assertTrue(PrimeIntegerCalculator.isPrime( 7));
        assertTrue(PrimeIntegerCalculator.isPrime( 11));
        assertTrue(PrimeIntegerCalculator.isPrime( 13));
        assertTrue(PrimeIntegerCalculator.isPrime( 17));
        assertTrue(PrimeIntegerCalculator.isPrime( 19));
        assertTrue(PrimeIntegerCalculator.isPrime(37));
        assertTrue(PrimeIntegerCalculator.isPrime(46349));
        assertTrue(PrimeIntegerCalculator.isPrime( 9973));
        assertTrue(PrimeIntegerCalculator.isPrime( 104729));
        assertTrue(PrimeIntegerCalculator.isPrime( 104743));
        assertTrue(PrimeIntegerCalculator.isPrime(999999937));
        assertTrue(PrimeIntegerCalculator.isPrime(100000007));

        
        assertFalse(PrimeIntegerCalculator.isPrime( 4));
        assertFalse(PrimeIntegerCalculator.isPrime( 9));
        assertFalse(PrimeIntegerCalculator.isPrime( 15));
        assertFalse(PrimeIntegerCalculator.isPrime( 100));
        assertFalse(PrimeIntegerCalculator.isPrime( 9975));
        assertFalse(PrimeIntegerCalculator.isPrime(561));
        assertFalse(PrimeIntegerCalculator.isPrime(1105));
        assertFalse(PrimeIntegerCalculator.isPrime(1729));
        assertFalse(PrimeIntegerCalculator.isPrime(2147483637));
    }

    /**
     * 测试 nextPrime 方法：验证给定整数后的下一个素数。
     */
    @Test
    void nextPrime() {
        
        assertEquals(2, PrimeIntegerCalculator.nextPrime( 0));
        assertEquals(2, PrimeIntegerCalculator.nextPrime( 1));
        assertEquals(3, PrimeIntegerCalculator.nextPrime( 2));
        assertEquals(5, PrimeIntegerCalculator.nextPrime( 3));
        assertEquals(5, PrimeIntegerCalculator.nextPrime( 4));
        assertEquals(7, PrimeIntegerCalculator.nextPrime( 5));
        assertEquals(7, PrimeIntegerCalculator.nextPrime( 6));
        assertEquals(11, PrimeIntegerCalculator.nextPrime( 7));
        assertEquals(11, PrimeIntegerCalculator.nextPrime( 8));
        assertEquals(13, PrimeIntegerCalculator.nextPrime( 12));
        assertEquals(17, PrimeIntegerCalculator.nextPrime( 13));
        assertEquals(19, PrimeIntegerCalculator.nextPrime( 17));
        assertEquals(137, PrimeIntegerCalculator.nextPrime(135));
        assertEquals(11111117, PrimeIntegerCalculator.nextPrime(11111111));
        assertEquals(100000007, PrimeIntegerCalculator.nextPrime(100000000));
        assertEquals(2147483647, PrimeIntegerCalculator.nextPrime(2147483637));
        assertEquals(2147483647, PrimeIntegerCalculator.nextPrime(2147483646));
        assertThrows(ArithmeticException.class,()-> PrimeIntegerCalculator.nextPrime(2147483647));
    }

    /**
     * 测试 primeCount 方法：验证不超过给定整数的素数个数。
     */
    @Test
    void primeCount() {
        assertEquals(0, PrimeIntegerCalculator.primeCount( -100));
        assertEquals(0, PrimeIntegerCalculator.primeCount( -10));
        assertEquals(0, PrimeIntegerCalculator.primeCount( -1));
        assertEquals(0, PrimeIntegerCalculator.primeCount( 0));   
        assertEquals(0, PrimeIntegerCalculator.primeCount( 1));   
        assertEquals(1, PrimeIntegerCalculator.primeCount( 2));   
        assertEquals(2, PrimeIntegerCalculator.primeCount( 3));   
        assertEquals(2, PrimeIntegerCalculator.primeCount( 4));   
        assertEquals(3, PrimeIntegerCalculator.primeCount( 5));   
        assertEquals(3, PrimeIntegerCalculator.primeCount( 6));   
        assertEquals(4, PrimeIntegerCalculator.primeCount( 10));  
        assertEquals(25, PrimeIntegerCalculator.primeCount( 100));                
        assertEquals(168, PrimeIntegerCalculator.primeCount( 1000));              
    }
    /**
     * 测试 primeOf 方法：验证第 n 个素数（1-indexed）的正确性及非法参数。
     */
    @Test
    void primeOf() {
        
        assertThrows(IllegalArgumentException.class, () -> PrimeIntegerCalculator.primeOf( 0));
        assertThrows(IllegalArgumentException.class, () -> PrimeIntegerCalculator.primeOf( -1));
        assertThrows(IllegalArgumentException.class, () -> PrimeIntegerCalculator.primeOf( -10000));
        assertThrows(IllegalArgumentException.class, () -> PrimeIntegerCalculator.primeOf( Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> PrimeIntegerCalculator.primeOf( Integer.MAX_VALUE));

        assertEquals(2, PrimeIntegerCalculator.primeOf( 1));
        assertEquals(3, PrimeIntegerCalculator.primeOf( 2));
        assertEquals(5, PrimeIntegerCalculator.primeOf( 3));
        assertEquals(7, PrimeIntegerCalculator.primeOf( 4));
        assertEquals(11, PrimeIntegerCalculator.primeOf( 5));
        assertEquals(13, PrimeIntegerCalculator.primeOf( 6));
        assertEquals(17, PrimeIntegerCalculator.primeOf( 7));
        assertEquals(19, PrimeIntegerCalculator.primeOf( 8));
        assertEquals(23, PrimeIntegerCalculator.primeOf( 9));
        assertEquals(29, PrimeIntegerCalculator.primeOf( 10));
    }
}
