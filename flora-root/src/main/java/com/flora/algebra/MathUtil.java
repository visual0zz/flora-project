package com.flora.algebra;

import com.flora.algebra.prime.PrimeIntegerCalculator;
import com.flora.algebra.prime.PrimeLongCalculator;
import com.flora.tag.SlowFunction;

/**
 * 数学工具类，提供素数判定与检索的静态方法。
 * <p>内部委托给 {@link PrimeIntegerCalculator} 和 {@link PrimeLongCalculator}
 * 分别处理 int 和 long 范围的运算。</p>
 */
public final class MathUtil {

    private MathUtil() {
    }

    // ==================== 素数相关方法 ====================

    /**
     * 判断给定的 int 值是否为素数。
     *
     * @param n 待判定的数
     * @return true 如果是素数
     */
    public static boolean isPrime(int n) {
        return PrimeIntegerCalculator.isPrime(n);
    }

    /**
     * 判断给定的 long 值是否为素数。
     *
     * @param n 待判定的数
     * @return true 如果是素数
     */
    public static boolean isPrime(long n) {
        return PrimeLongCalculator.isPrime(n);
    }

    /**
     * 返回大于 n 的最小素数（int 范围）。
     *
     * @throws ArithmeticException 如果结果溢出 int
     */
    public static int nextPrime(int n) {
        return PrimeIntegerCalculator.nextPrime(n);
    }

    /**
     * 返回大于 n 的最小素数（long 范围）。
     *
     * @throws ArithmeticException 如果结果溢出 long
     */
    public static long nextPrime(long n) {
        return PrimeLongCalculator.nextPrime(n);
    }

    /**
     * 返回小于 n 的最大素数（int 范围）。
     *
     * @throws IllegalArgumentException 如果 n ≤ 2
     */
    public static int prevPrime(int n) {
        return PrimeIntegerCalculator.prevPrime(n);
    }

    /**
     * 返回小于 n 的最大素数（long 范围）。
     *
     * @throws IllegalArgumentException 如果 n ≤ 2
     */
    public static long prevPrime(long n) {
        return PrimeLongCalculator.prevPrime(n);
    }

    /**
     * 返回不超过 x 的素数个数
     */
    public static int primeCount(int x) {
        return PrimeIntegerCalculator.primeCount(x);
    }

    /**
     * 返回第 n 个素数，从1开始计数
     *
     * @throws IllegalArgumentException 如果 n ≤ 0 或 n 过大
     */
    @SlowFunction(seconds = 2)
    public static int primeOf(int n) {
        return PrimeIntegerCalculator.primeOf(n);
    }

    /**
     * 返回 [num1, num2] 区间内素数的个数。
     */
    public static int primeCount(int num1, int num2) {
        return PrimeIntegerCalculator.primeCount(num1, num2);
    }
}