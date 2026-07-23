package com.flora.algebra.prime;

import com.flora.fast.container.map.Long2LongFastHashMap;
import com.flora.tag.CriticallyOptimized;
import com.flora.tag.LogicFragile;
import com.flora.tag.SlowFunction;

import java.util.Arrays;

/**
 * 素数相关工具类（int 范围版本）。
 * <p>
 * 提供素数计数函数 π(x)、第 n 个素数、下一个素数、确定性素性判定等功能。
 * 内部通过埃氏筛预处理 √(Integer.MAX_VALUE) 以内的基底素数表，
 * 并基于 Lehmer 完整公式（a = π(x^{1/3}) + P2 修正）实现高效的 π(x) 计算。
 * </p>
 * <p>所有方法均为静态方法，不可实例化。</p>
 *
 */

public final class PrimeIntegerCalculator {
    // ==================== API ====================

    /**
     * 判定 int 值是否为素数。
     * <p>对小值使用二分查找基准素数表；对大值采用基 {2, 7, 61} 的确定性 Miller-Rabin 测试。</p>
     *
     * @param n 待判定整数
     * @return true 如果 n 是素数
     */
    public static boolean isPrime(int n) {
        if (n < 2) return false;
        // ★ 小范围直接查表，短路掉 Miller-Rabin
        if (n <= basePrimes[basePrimes.length - 1]) {
            return Arrays.binarySearch(basePrimes, n) >= 0;
        }
        // 大数才走 Miller-Rabin
        int d = n - 1, s = 0;
        while ((d & 1) == 0) {
            d >>= 1;
            s++;
        }
        for (int a : MR_BASES) {
            long x = modPow(a, d, n);
            if (x == 1 || x == n - 1) continue;
            boolean composite = true;
            for (int r = 1; r < s; r++) {
                x = (x * x) % n;
                if (x == n - 1) {
                    composite = false;
                    break;
                }
            }
            if (composite) return false;
        }
        return true;
    }

    /**
     * 返回大于等于 n 的最小素数。
     *
     * @param n 基准值
     * @return 不小于 n 的最小素数
     * @throws ArithmeticException 查找过程中溢出
     */
    public static int nextPrime(int n) {
        if (n < 2) return 2;
        int candidate = n + 1;
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
     * @param n 基准值
     * @return 不大于 n 的最大素数
     * @throws IllegalArgumentException 不存在小于 2 的素数
     */
    public static int prevPrime(int n) {
        if (n <= 2) throw new IllegalArgumentException("No prime less than 2");
        if (n == 3) return 2;
        int candidate = n % 2 == 0 ? n - 1 : n - 2;  // 对齐到奇数
        while (!PrimeIntegerCalculator.isPrime(candidate)) {
            candidate -= 2;
        }
        return candidate;
    }

    /**
     * 计算不超过 x 的素数个数 π(x)，采用 Lehmer 素数计数算法。
     *
     * @param x 上限
     * @return 不超过 x 的素数个数
     */
    public static int primeCount(int x) {
        if (x < 2) return 0;
        if (x <= basePrimes[basePrimes.length - 1]) {
            int idx = Arrays.binarySearch(basePrimes, x);
            return idx >= 0 ? idx + 1 : -idx - 1;
        }
        var cache = new Long2LongFastHashMap();
        cache.defaultReturnValue(Long.MIN_VALUE);
        return primeCount(x, cache);
    }

    /**
     * 计算闭区间 [num1, num2] 内的素数个数。
     * <p>根据区间长度和数值范围自适应选择策略：小范围使用 Sieve/暴力计数，
     * 大范围退化为 π(num2) - π(num1-1) 的 Lehmer 计算。</p>
     *
     * @param num1 区间起点
     * @param num2 区间终点
     * @return 区间内的素数个数
     */

    @CriticallyOptimized
    public static int primeCount(int num1, int num2) {
        if (num1 > num2) {
            int tmp = num1;
            num1 = num2;
            num2 = tmp;
        }
        if (num2 < 2) return 0;
        if (num1 < 0) num1 = 0;

        int len = num2 - num1 + 1;

        // ===== 根据区间长度和端点大小自动选择最快算法 =====
        if (num2 <= 46340) {
            // 极小值直接查表
            return primeCount(num2) - primeCount(num1 - 1);
        } else if (len <= 1000) {
            // 极短区间直接暴力，不做估算
            return brutePrimeCount(num1, num2);
        } else {
            // 估算暴力遍历耗时（纳秒）
            long bruteTimeNs = (long) len * ((num1 > 10000000 ? 140 : 70) + (num2 > 10000000 ? 140 : 70)) / 2;

            // 估算 Lehmer 算法耗时（纳秒）：两次 Lehmer，每次 k * cbrt(x)^2
            // 使用 long 加法避免 int 溢出（等价于 ((long)num1 + num2) * 7 / 100）
            long lehmerTimeNs = ((long) num1 + num2) * 7L / 100;

            if (bruteTimeNs < lehmerTimeNs) {
                return brutePrimeCount(num1, num2);
            } else {
                return primeCount(num2) - primeCount(num1 - 1);
            }
        }
    }


    /**
     * 返回第 n 个素数（通过素数定理估算上界后二分搜索 {@link #primeCount}）。
     *
     * @param n 序号（正整数）
     * @return 第 n 个素数
     * @throws IllegalArgumentException n ≤ 0 或 n 超过最大支持值（105,097,565）
     */
    @SlowFunction(seconds = 2)
    public static int primeOf(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        if (n > 105_097_565) throw new IllegalArgumentException("n is too large");
        if (n <= basePrimes.length) return basePrimes[n - 1];

        double logN = Math.log(n);
        double logLogN = Math.log(logN);
        int lo = (int) Math.max(2, (long) (n * (logN + logLogN - 1)));
        int hi = (int) Math.min(Integer.MAX_VALUE, (long) (n * (logN + logLogN)) + 1);


        var sharedCache = new Long2LongFastHashMap();
        sharedCache.defaultReturnValue(Long.MIN_VALUE);
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (primeCount(mid, sharedCache) < n) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }



    private PrimeIntegerCalculator() {
    }

    /**
     * Miller-Rabin 测试的确定性基。对 int 范围（&lt; 2³²）的确定性判定仅需基 {2, 7, 61}。
     */
    private static final int[] MR_BASES = {2, 7, 61};
    /**
     * 预计算的基准素数表（埃拉托色尼筛法，上限 √Integer.MAX_VALUE ≈ 46341）。
     * 用于小值快速判定和 Lehmer 算法的 P2/φ 计算。
     */
    private static final int[] basePrimes;

    static {
        int sqrt = 46341;
        boolean[] sieve = new boolean[sqrt + 1];
        Arrays.fill(sieve, true);
        sieve[0] = sieve[1] = false;
        for (int i = 2; i * i <= sqrt; i++) {
            if (sieve[i]) {
                for (int j = i * i; j <= sqrt; j += i)
                    sieve[j] = false;
            }
        }
        int cnt = 0;
        for (boolean b : sieve) if (b) cnt++;
        basePrimes = new int[cnt];
        int idx = 0;
        for (int i = 2; i <= sqrt; i++)
            if (sieve[i]) basePrimes[idx++] = i;
    }

    /**
     * 计算整数 x 的立方根（向下取整），用于 Lehmer 算法中参数 y = ⌊∛x⌋。
     */
    private static long cbrt(long x) {
        long lo = 1, hi = 1291;
        while (lo < hi) {
            long mid = lo + (hi - lo + 1) / 2;
            long cube = mid * mid * mid;
            if (cube <= x) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    // ==================== 核心 ====================

    /**
     * 暴力统计区间 [num1, num2] 内的素数个数（逐奇数判定）。
     * 仅在区间长度很小时使用。
     */
    private static int brutePrimeCount(int num1, int num2) {
        int count = 0;
        if (num1 <= 2) count++;
        int start = num1;
        if (start < 3) start = 3;
        if ((start & 1) == 0) start++;
        for (int n = start; n <= num2; n += 2) {
            if (isPrime(n)) count++;
        }
        return count;
    }

    /**
     * Lehmer 素数计数 π(x) 核心实现：π(x) = φ(x, a) + a - 1 - P2(x, a)。
     * <p>其中 a = π(⌊∛x⌋)，使用缓存加速重复计算。</p>
     *
     * @param x     上限
     * @param cache 共享的 φ/P2 缓存
     * @return π(x)
     */
    private static int primeCount(int x, Long2LongFastHashMap cache) {
        int y = (int) cbrt(x);
        int idx = Arrays.binarySearch(basePrimes, y);
        int a = idx >= 0 ? idx + 1 : -idx - 1;

        long phiVal = phi(x, a, cache);
        long p2Val = P2(x, a, cache);
        return Math.toIntExact(phiVal + a - 1 - p2Val);
    }

    /**
     * Lehmer φ 函数（Legendre 公式的递归变体），计算不超过 x 且不被前 a 个素数整除的正整数个数。
     * <p>φ(x, a) = φ(x, a-1) - φ(x / pₐ, a-1)，使用 {@code (x << 32) | a} 作为缓存键。</p>
     */
    private static long phi(long x, int a, Long2LongFastHashMap cache) {
        if (x <= 1) return x;
        if (a == 1) return x - x / 2;

        long key = ((long) x << 32) | a;
        long cached = cache.get(key);
        if (cached != Long.MIN_VALUE) return cached;

        long result = phi(x, a - 1, cache)
                - phi(x / basePrimes[a - 1], a - 1, cache);
        cache.put(key, result);
        return result;
    }

    /**
     * Lehmer P2 函数，计算 π(x) 修正项中的二元半素数计数部分。
     * <p>P2(x, a) = Σ_{i=a}^{π(√x)} (π(x/pᵢ) - i + 1)，其中 pᵢ 是第 i 个素数。</p>
     */
    private static long P2(int x, int a, Long2LongFastHashMap cache) {
        long sum = 0;
        for (int i = a; i < basePrimes.length; i++) {
            long p = basePrimes[i];
            if (p * p > x) break;

            int xp = x / (int) p;
            long piXp;

            if (xp <= basePrimes[basePrimes.length - 1]) {
                int id = Arrays.binarySearch(basePrimes, xp);
                piXp = id >= 0 ? id + 1 : -id - 1;
            } else {
                int sqrtXp = (int) Math.sqrt(xp);
                int id = Arrays.binarySearch(basePrimes, sqrtXp);
                int a2 = id >= 0 ? id + 1 : -id - 1;
                piXp = phi(xp, a2, cache) + a2 - 1;
            }

            sum += piXp - i;
        }
        return sum;
    }

    /**
     * 快速幂模运算 a^exp mod m。
     * 用于 Miller-Rabin 测试中的模幂计算。
     */
    private static long modPow(long a, long exp, int m) {
        long res = 1;
        a %= m;
        while (exp > 0) {
            if ((exp & 1) == 1) res = (res * a) % m;
            a = (a * a) % m;
            exp >>= 1;
        }
        return res;
    }
}