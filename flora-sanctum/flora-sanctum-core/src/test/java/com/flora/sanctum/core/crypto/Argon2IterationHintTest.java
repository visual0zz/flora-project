package com.flora.sanctum.core.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Argon2KDF#suggestIterationsForOneSecond} 的纯算术用例。
 * <p>不执行真实 Argon2 计算，快速，不标记 slow。</p>
 */
class Argon2IterationHintTest {

    @Test
    void picksIntegerNearestToOneSecond() {
        // 单次 1.23 秒（当前硬件/参数组实测基线）：1 次即最接近 1 秒
        assertEquals(1, Argon2KDF.suggestIterationsForOneSecond(1.23));
        // 2×0.6=1.2 比 1×0.6=0.6 更接近 1 秒
        assertEquals(2, Argon2KDF.suggestIterationsForOneSecond(0.6));
        // 20×0.05=1.0
        assertEquals(20, Argon2KDF.suggestIterationsForOneSecond(0.05));
        // 单次已超过 1 秒：只能取 1 次
        assertEquals(1, Argon2KDF.suggestIterationsForOneSecond(2.5));
    }

    @Test
    void tiePrefersSmallerIterations() {
        // 3×0.2857…=0.857 与 4×0.2857…=1.143 等距于 1 秒：取较小的 3
        assertEquals(3, Argon2KDF.suggestIterationsForOneSecond(1.0 / 3.5));
        // 2×0.4=0.8 与 3×0.4=1.2 等距于 1 秒：取较小的 2
        assertEquals(2, Argon2KDF.suggestIterationsForOneSecond(0.4));
    }

    @Test
    void rejectsNonPositiveOrNonFinite() {
        assertThrows(IllegalArgumentException.class, () -> Argon2KDF.suggestIterationsForOneSecond(0.0));
        assertThrows(IllegalArgumentException.class, () -> Argon2KDF.suggestIterationsForOneSecond(-1.0));
        assertThrows(IllegalArgumentException.class, () -> Argon2KDF.suggestIterationsForOneSecond(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Argon2KDF.suggestIterationsForOneSecond(Double.POSITIVE_INFINITY));
    }
}
