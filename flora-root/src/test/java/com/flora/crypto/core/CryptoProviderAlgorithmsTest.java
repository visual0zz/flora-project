package com.flora.crypto.core;

import com.flora.crypto.core.bridge.JdkKem;
import com.flora.crypto.core.interfaces.provider.DerivationFunction;
import com.flora.crypto.core.interfaces.provider.Digest;
import com.flora.crypto.core.interfaces.provider.KEM;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.PlaceholderKem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoProviderAlgorithmsTest {

    // 纯 Java 摘要实现接入 DSL（含带参与无参别名）
    @Test
    void pureJavaDigestsResolve() {
        assertEquals(32, CryptoProvider.digest("BLAKE2B-256").getDigestSize());
        assertEquals(64, CryptoProvider.digest("BLAKE2B-512").getDigestSize());
        assertEquals(20, CryptoProvider.digest("Ripemd160").getDigestSize());
        assertEquals(20, CryptoProvider.digest("RIPEMD160").getDigestSize());
        // 带参形式按输出长度构造
        Digest b = CryptoProvider.digest("Blake2b(integer:32)");
        assertEquals(32, b.getDigestSize());
    }

    // 带参算法不得裸名调用
    @Test
    void blake2bRequiresParameter() {
        assertThrows(IllegalArgumentException.class, () -> CryptoProvider.digest("Blake2b"));
    }

    @Test
    void poly1305MacResolves() {
        Mac m = CryptoProvider.mac("Poly1305");
        assertNotNull(m);
        assertTrue(m.getMacSize() > 0);
    }

    @Test
    void passwordHashesResolve() {
        assertNotNull(CryptoProvider.derivationFunction("Argon2"));
        assertNotNull(CryptoProvider.derivationFunction("BCrypt"));
        assertNotNull(CryptoProvider.derivationFunction("Scrypt"));
    }

    // JdkKem 接入后 ML-KEM 是真实实现，而非无功能占位符
    @Test
    void mlKemIsRealImplementationNotPlaceholder() {
        KEM kem = CryptoProvider.kem("ML-KEM");
        assertNotNull(kem);
        assertFalse(kem instanceof PlaceholderKem);
        assertInstanceOf(JdkKem.class, kem);
    }
}
