package com.flora.sanctum.core.crypto;

import com.flora.sanctum.core.crypto.impl.SecureRandomSource;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureRandomSourceTest {

    private static final SecureRandomSource RNG = new SecureRandomSource();

    @Test
    void nextUuidIsRfc4122V4() {
        UUID u = RNG.nextUuid();
        assertEquals(4, u.version(), "version 应为 4");
        assertEquals(2, u.variant(), "variant 应为 RFC 4122");
    }

    @Test
    void nextUuidUniqueAcrossManyDraws() {
        int n = 10_000;
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            seen.add(RNG.nextUuid());
        }
        assertEquals(n, seen.size(), "10k 次生成不应碰撞");
    }

    @Test
    void nextUuidDistributionOverShardingBuckets() {
        // uuid 前两字符决定存储分片目录（256 桶），抽样应大致均匀（不产生冷/热桶）
        int n = 8192;
        int[] buckets = new int[256];
        for (int i = 0; i < n; i++) {
            String hex = RNG.nextUuid().toString().replace("-", "");
            buckets[Integer.parseInt(hex.substring(0, 2), 16)]++;
        }
        int mean = n / 256;
        for (int c : buckets) {
            assertTrue(c > mean / 4 && c < mean * 4, "bucket count out of range: " + c);
        }
    }
}
