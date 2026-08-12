package com.flora.root.crypto.core.combinator;

import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.common.register.AlgorithmFactoryRegister;
import com.flora.root.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.root.crypto.core.interfaces.algorithm.AsymmetricBlockCipher;
import com.flora.root.crypto.core.interfaces.algorithm.AsymmetricScheme;
import com.flora.root.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.root.java.CheckUtil;

import java.security.SecureRandom;
import java.util.Set;

/**
 * 非对称消息编码方案组合器。
 * <p>把裸非对称引擎（{@link AsymmetricBlockCipher}，如 {@code "RSA"}）与消息编码方案
 * （{@link AsymmetricScheme}，如 OAEP / PKCS1v1.5）组合为完整的非对称加解密：
 * 加密方向先 {@code scheme.encode} 后引擎裸运算，解密方向先引擎裸运算后 {@code scheme.decode}。</p>
 */
public final class PaddedAsymmetricBlockCipher implements AsymmetricBlockCipher {

    private final AsymmetricBlockCipher engine;
    private final AsymmetricScheme scheme;
    private boolean encrypting;

    public PaddedAsymmetricBlockCipher(AsymmetricBlockCipher engine, AsymmetricScheme scheme) {
        CheckUtil.notNull(engine, "底层非对称引擎不能为空");
        CheckUtil.notNull(scheme, "消息编码方案不能为空");
        this.engine = engine;
        this.scheme = scheme;
    }

    @Override
    public void init(boolean forEncryption, CipherParameter params) {
        this.encrypting = forEncryption;
        engine.init(forEncryption, params);
        scheme.init(forEncryption, params, new SecureRandom());
    }

    @Override
    public String getAlgorithmName() {
        return "PaddedAsymmetricBlockCipher";
    }

    @Override
    public int getInputBlockSize() {
        return encrypting ? scheme.getInputBlockSize() : engine.getOutputBlockSize();
    }

    @Override
    public int getOutputBlockSize() {
        return engine.getOutputBlockSize();
    }

    @Override
    public byte[] processBlock(byte[] in, int inOff, int len) {
        if (encrypting) {
            byte[] encoded = scheme.encode(in, inOff, len);
            return engine.processBlock(encoded, 0, encoded.length);
        }
        byte[] raw = engine.processBlock(in, inOff, len);
        return scheme.decode(raw, 0, raw.length);
    }

    @Override
    public AlgorithmFactory<? extends AsymmetricBlockCipher> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<AsymmetricBlockCipher> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("PaddedAsymmetricBlockCipher");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[]{AsymmetricBlockCipher.class, AsymmetricScheme.class};
        }

        @Override
        public AsymmetricBlockCipher construct(String algorithmName, AlgorithmComponent... components) {
            return new PaddedAsymmetricBlockCipher(
                    (AsymmetricBlockCipher) components[0], (AsymmetricScheme) components[1]);
        }
    };
}
