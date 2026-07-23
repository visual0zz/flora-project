package com.flora.algebra.prime;


/**
 * long 范围素数计算器，采用确定性 Miller-Rabin 素性测试。
 * <p>适用于 long（64 位）范围的素数判定及前后素数查找。
 * 内部使用 {2, 325, 9375, 28178, 450775, 9780504, 1795265022} 作为确定性基，
 * 对 2⁶⁴ 以内的奇数可给出确定的判定结果。</p>
 */
public final class PrimeLongCalculator {

    private PrimeLongCalculator() {}

    /**
     * 15 个最小素数，用于快速筛选（试除预检）。
     */
    private static final long[] SMALL_PRIMES = {
            2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47
    };

    /**
     * Miller-Rabin 确定性测试基。这 7 个基对 2⁶⁴ 以内的所有奇数给出确定的素性判定。
     */
    private static final long[] MR_BASES = {
            2, 325, 9375, 28178, 450775, 9780504, 1795265022
    };

    /**
     * 判定 long 值是否为素数。
     * <p>先通过小素数表试除快速排除合数，再执行确定性 Miller-Rabin 测试。</p>
     *
     * @param n 待判定长整数
     * @return true 如果 n 是素数
     */
    public static boolean isPrime(long n) {
        if (n < 2) return false;

        
        for (long p : SMALL_PRIMES) {
            if (n == p) return true;
            if (n % p == 0) return false;
        }

        
        long d = n - 1;
        int s = 0;
        while ((d & 1) == 0) { d >>= 1; s++; }

        for (long a : MR_BASES) {
            
            long x = modPow(a, d, n);
            if (x == 1 || x == n - 1) continue;

            boolean composite = true;
            for (int r = 1; r < s; r++) {
                x = mulMod(x, x, n);
                if (x == n - 1) { composite = false; break; }
            }
            if (composite) return false;
        }
        return true;
    }

    /**
     * 返回大于等于 n 的最小素数。
     *
     * @param n 基准长整数
     * @return 不小于 n 的最小素数
     * @throws ArithmeticException 查找过程中溢出（超出 Long.MAX_VALUE）
     */
    public static long nextPrime(long n) {
        if (n < 2) return 2;
        
        long candidate = n + 1;
        if ((candidate & 1) == 0) candidate++;

        while (!isPrime(candidate)) {
            candidate += 2;
            if (candidate < 0) throw new ArithmeticException("overflow");
        }
        return candidate;
    }

    /**
     * 返回小于等于 n 的最大素数（从奇数向小扫描）。
     *
     * @param n 基准长整数
     * @return 不大于 n 的最大素数
     * @throws IllegalArgumentException 不存在小于 2 的素数
     */
    public static long prevPrime(long n) {
        if (n <= 2) throw new IllegalArgumentException("No prime less than 2");
        if(n==3) return 2;
        long candidate = n%2==0?n - 1:n-2;
        while (!PrimeLongCalculator.isPrime(candidate)) {
            candidate -= 2;
        }
        return candidate;
    }

    

    /**
     * 无溢出模乘 a * b % mod。
     * <p>优先使用 Java 18+ {@code Math.multiplyHigh} 检测溢出；
     * 若结果不超过 63 位则直接计算取模，否则退化为二进制长乘法。</p>
     */
    private static long mulMod(long a, long b, long mod) {
        long lo = a * b;
        long hi = Math.multiplyHigh(a, b);
        if (hi == 0 && lo >= 0) return lo % mod;

        
        long res = 0;
        a %= mod;
        while (b > 0) {
            if ((b & 1) == 1) res = addMod(res, a, mod);
            a = addMod(a, a, mod);
            b >>= 1;
        }
        return res;
    }

    /**
     * 无溢出模加 a + b % mod。
     * <p>当 mod 不超过 Long.MAX_VALUE/2 时使用减法法；
     * 否则通过比较 a ≥ mod - b 来避免溢出。</p>
     */
    private static long addMod(long a, long b, long mod) {
        if (mod <= Long.MAX_VALUE / 2) {
            long s = a + b;
            return s >= mod ? s - mod : s;
        }
        long diff = mod - b;
        return a >= diff ? a - diff : a + b;
    }

    /**
     * 快速幂模运算 base^exp % mod。
     * 用于 Miller-Rabin 测试中的大数模幂计算。
     */
    private static long modPow(long base, long exp, long mod) {
        long res = 1;
        base %= mod;
        while (exp > 0) {
            if ((exp & 1) == 1) res = mulMod(res, base, mod);
            base = mulMod(base, base, mod);
            exp >>= 1;
        }
        return res;
    }
}