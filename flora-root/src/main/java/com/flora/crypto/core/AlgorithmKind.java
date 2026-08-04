package com.flora.crypto.core;

import com.flora.crypto.core.bridge.JdkKeyPairGenerator;
import com.flora.crypto.core.interfaces.provider.AEADBlockCipher;
import com.flora.crypto.core.interfaces.provider.Agreement;
import com.flora.crypto.core.interfaces.provider.AsymmetricBlockCipher;
import com.flora.crypto.core.interfaces.provider.AsymmetricCipher;
import com.flora.crypto.core.interfaces.provider.AsymmetricCipherKeyPairGenerator;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.provider.BlockCipherPadding;
import com.flora.crypto.core.interfaces.provider.DerivationFunction;
import com.flora.crypto.core.interfaces.provider.Digest;
import com.flora.crypto.core.interfaces.provider.EntropySource;
import com.flora.crypto.core.interfaces.provider.ExtendedDigest;
import com.flora.crypto.core.interfaces.provider.KEM;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.interfaces.provider.SP80090DRBG;
import com.flora.crypto.core.interfaces.provider.Xof;

/**
 * 算法族分类（枚举），替代 {@link CryptoProvider} 注册表中 {@code Class<?> role} 维度。
 * <p>每个常量持有对应的角色接口类型，供类型化查询做强制转换与跨族解析。</p>
 */
public enum AlgorithmKind {

    DIGEST(Digest.class),
    EXTENDED_DIGEST(ExtendedDigest.class),
    BLOCK_CIPHER(BlockCipher.class),
    MAC(Mac.class),
    ASYMMETRIC_BLOCK_CIPHER(AsymmetricBlockCipher.class),
    ASYMMETRIC_CIPHER(AsymmetricCipher.class),
    AGREEMENT(Agreement.class),
    KEM(KEM.class),
    DERIVATION(DerivationFunction.class),
    BLOCK_CIPHER_PADDING(BlockCipherPadding.class),
    ASYMMETRIC_KEY_PAIR_GENERATOR(AsymmetricCipherKeyPairGenerator.class),
    KEY_PAIR_GENERATOR(JdkKeyPairGenerator.class),
    ENTROPY_SOURCE(EntropySource.class),
    XOF(Xof.class),
    DRBG(SP80090DRBG.class),
    AEAD_BLOCK_CIPHER(AEADBlockCipher.class);

    private final Class<?> roleClass;

    AlgorithmKind(Class<?> roleClass) {
        this.roleClass = roleClass;
    }

    /** @return 该算法族对应的角色接口类型 */
    public Class<?> roleClass() {
        return roleClass;
    }
}
