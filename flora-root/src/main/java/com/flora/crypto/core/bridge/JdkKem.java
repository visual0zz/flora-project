package com.flora.crypto.core.bridge;

import com.flora.tag.ThreadFragile;
import com.flora.crypto.core.keypair.AsymmetricKeyParameter;
import com.flora.crypto.core.impl.SecretWithEncapsulationImpl;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.interfaces.Decapsulator;
import com.flora.crypto.core.interfaces.Encapsulator;
import com.flora.crypto.core.interfaces.SecretWithEncapsulation;
import com.flora.java.CheckUtil;

import javax.crypto.KEM;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * 把 JDK 自带的 {@link KEM}（ML-KEM，Java 21+）接入 flora 的 {@link KEM} 界面。
 * <p>实现封装/解封装语义：发送方用接收方公钥封装出共享密钥与封装密文，接收方用私钥解封装还原。
 * 取代 {@code PlaceholderKem} 占位实现。</p>
 */
@ThreadFragile
public final class JdkKem implements com.flora.crypto.core.interfaces.provider.KEM {

    private final String algorithm;
    private final KEM kem;

    private JdkKem(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.kem = KEM.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的 KEM 算法: " + algorithm, e);
        }
    }

    public static JdkKem of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkKem(algorithm);
    }

    public static final Set<String> SUPPORTED = Set.of("ML-KEM");

    @Override
    public Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public Encapsulator newEncapsulator(CipherParameters publicKey) {
        try {
            return new JdkEncapsulator(algorithm,
                    kem.newEncapsulator(asKey(publicKey, java.security.PublicKey.class, "公钥")));
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalArgumentException("创建封装器失败: " + algorithm, e);
        }
    }

    @Override
    public Decapsulator newDecapsulator(CipherParameters privateKey) {
        try {
            return new JdkDecapsulator(algorithm,
                    kem.newDecapsulator(asKey(privateKey, java.security.PrivateKey.class, "私钥")));
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalArgumentException("创建解封装器失败: " + algorithm, e);
        }
    }

    private static <T extends Key> T asKey(CipherParameters params, Class<T> type, String what) {
        CheckUtil.notNull(params, "密钥参数不能为空");
        if (!(params instanceof AsymmetricKeyParameter)) {
            throw new IllegalArgumentException("KEM 需要 AsymmetricKeyParameter（" + what + "）");
        }
        Key key = ((AsymmetricKeyParameter) params).getKey();
        if (!type.isInstance(key)) {
            throw new IllegalArgumentException("KEM " + what + "类型不匹配: " + key.getClass().getName());
        }
        return type.cast(key);
    }

    private static final class JdkEncapsulator implements Encapsulator {
        private final String algorithm;
        private final KEM.Encapsulator enc;

        JdkEncapsulator(String algorithm, KEM.Encapsulator enc) {
            this.algorithm = algorithm;
            this.enc = enc;
        }

        @Override
        public int getEncapsulationLength() {
            return enc.encapsulationSize();
        }

        @Override
        public int getSecretLength() {
            return enc.secretSize();
        }

        @Override
        public SecretWithEncapsulation encapsulate() {
            KEM.Encapsulated e = enc.encapsulate();
            return new SecretWithEncapsulationImpl(e.key().getEncoded(), e.encapsulation());
        }
    }

    private static final class JdkDecapsulator implements Decapsulator {
        private final String algorithm;
        private final KEM.Decapsulator dec;

        JdkDecapsulator(String algorithm, KEM.Decapsulator dec) {
            this.algorithm = algorithm;
            this.dec = dec;
        }

        @Override
        public int getEncapsulationLength() {
            return dec.encapsulationSize();
        }

        @Override
        public int getSecretLength() {
            return dec.secretSize();
        }

        @Override
        public SecretWithEncapsulation decapsulate(byte[] encapsulation) {
            CheckUtil.notNull(encapsulation, "封装密文不能为空");
            try {
                return new SecretWithEncapsulationImpl(dec.decapsulate(encapsulation).getEncoded(),
                        encapsulation);
            } catch (javax.crypto.DecapsulateException e) {
                throw new IllegalStateException("解封装失败: " + algorithm, e);
            }
        }
    }
}
