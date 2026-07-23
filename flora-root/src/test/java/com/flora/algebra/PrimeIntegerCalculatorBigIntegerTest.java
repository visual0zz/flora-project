package com.flora.algebra;

import com.flora.algebra.prime.PrimeIntegerCalculator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrimeIntegerCalculator 的大数范围单元测试。
 * 测试大整数范围内的素数计数、第 n 个素数查找及素数判断的一致性。
 */
@Tag("slow")
class PrimeIntegerCalculatorBigIntegerTest {

    /**
     * 测试大整数范围内 primeCount、primeOf、isPrime 和 nextPrime 的正确性。
     * 覆盖千万级到 int 最大值范围的素数统计。
     */
    @Test
    void bigNumber(){
        
        assertEquals(1229, PrimeIntegerCalculator.primeCount(10000));              
        assertEquals(9592, PrimeIntegerCalculator.primeCount(100000));             
        assertEquals(78498, PrimeIntegerCalculator.primeCount(1000000));           
        assertEquals(664579, PrimeIntegerCalculator.primeCount(10000000));         
        assertEquals(5761455, PrimeIntegerCalculator.primeCount(100000000));       
        assertEquals(50847534, PrimeIntegerCalculator.primeCount(1000000000));     
        assertEquals(105097565, PrimeIntegerCalculator.primeCount(2147483647));    

        
        assertEquals(104729, PrimeIntegerCalculator.primeOf(10000));               
        assertEquals(611953, PrimeIntegerCalculator.primeOf(50000));               
        assertEquals(1299709, PrimeIntegerCalculator.primeOf(100000));             
        assertEquals(2750159, PrimeIntegerCalculator.primeOf(200000));             
        assertEquals(7368787, PrimeIntegerCalculator.primeOf(500000));             
        assertEquals(15485863, PrimeIntegerCalculator.primeOf(1000000));           
        assertEquals(32452843, PrimeIntegerCalculator.primeOf(2000000));           
        assertEquals(2147483647, PrimeIntegerCalculator.primeOf(105097565));       

        
        assertTrue(PrimeIntegerCalculator.isPrime(2147483647));                   
        assertTrue(PrimeIntegerCalculator.isPrime(2147483629));
        assertTrue(PrimeIntegerCalculator.isPrime(1000000007));
        assertTrue(PrimeIntegerCalculator.isPrime(999999937));

        
        assertFalse(PrimeIntegerCalculator.isPrime(2147483646));

        
        assertEquals(1000000007, PrimeIntegerCalculator.nextPrime(1000000000));
        assertEquals(2147483629, PrimeIntegerCalculator.nextPrime(2147483587));
        assertEquals(2147483647, PrimeIntegerCalculator.nextPrime(2147483629));
    }

    /**
     * 测试 primeOf/primeCount/nextPrime/isPrime 在 1^3 ~ 100^3 范围内的一致性。
     * 验证素数计数与第 n 个素数互为逆运算。
     */
    @Test
    void consistency(){
        
        for (int i = 1; i <= 100; i++) {
            int n=i*i*i;
            int thisPrime = PrimeIntegerCalculator.primeOf(n);
            int nextPrime = PrimeIntegerCalculator.nextPrime(thisPrime);
            String errorMsg=String.format("n=%d,thisPrime=%d,nextPrime=%d", n,thisPrime,nextPrime);

            assertTrue(PrimeIntegerCalculator.isPrime(thisPrime),errorMsg); 
            assertTrue(PrimeIntegerCalculator.isPrime(nextPrime),errorMsg); 
            if(n>=6){
                double v = Math.log(n) + Math.log(Math.log(n));
                assertTrue((double) thisPrime/n > v -1);
                assertTrue((double) thisPrime/n < v);
            }
            assertTrue(nextPrime > thisPrime && thisPrime > n,errorMsg); 
            assertEquals(n, PrimeIntegerCalculator.primeCount(thisPrime),errorMsg);  
            assertEquals(n+1, PrimeIntegerCalculator.primeCount(nextPrime),errorMsg);

            if (thisPrime > 2) {
                assertEquals(n - 1, PrimeIntegerCalculator.primeCount(thisPrime - 1));
            }
        }
    }
}
