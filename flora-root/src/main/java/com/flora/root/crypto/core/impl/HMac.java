package com.flora.root.crypto.core.impl;

import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.common.register.AlgorithmFactoryRegister;
import com.flora.root.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.root.crypto.core.bridge.JdkMac;
import com.flora.root.crypto.core.interfaces.algorithm.Digest;
import com.flora.root.crypto.core.interfaces.algorithm.Mac;
import com.flora.root.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.root.crypto.core.interfaces.material.param.KeyParameter;
import com.flora.root.java.CheckUtil;

import java.util.Arrays;
import java.util.Set;

/**
 * HMAC（RFC 2104）自研实现，以 newcore {@link Digest} 为哈希原语。
 * <p>与委托 JDK 的 {@link JdkMac} 互补：JDK 的
 * {@code javax.crypto.Mac} 拒绝空密钥，本实现支持任意长度密钥（含 0 字节），满足
 * PBKDF2/scrypt 的空口令等标准场景。构造时注入底层 {@link Digest} 组件。</p>
 */
public final class HMac implements Mac {

    private final Digest digest;
    private final int blockSize;

    private final byte[] ipad;
    private final byte[] opad;

    public HMac(Digest digest) {
        CheckUtil.notNull(digest, "HMAC 底层摘要不能为空");
        this.digest = digest;
        this.blockSize = digest.getInternalBlockLength();
        this.ipad = new byte[blockSize];
        this.opad = new byte[blockSize];
    }

    @Override
    public String getAlgorithmName() {
        return "HMAC";
    }

    @Override
    public int getMacSize() {
        return digest.getDigestResultSize();
    }

    @Override
    public void init(CipherParameter params) {
        if (!(params instanceof KeyParameter)) {
            throw new IllegalArgumentException("HMAC 需要 KeyParameter");
        }
        byte[] key = ((KeyParameter) params).getKey();
        byte[] kPad;
        if (key.length > blockSize) {
            digest.reset();
            digest.update(key, 0, key.length);
            byte[] hashed = new byte[digest.getDigestResultSize()];
            digest.doFinal(hashed, 0);
            kPad = new byte[blockSize];
            System.arraycopy(hashed, 0, kPad, 0, hashed.length);
            Arrays.fill(hashed, (byte) 0);
        } else {
            kPad = new byte[blockSize];
            System.arraycopy(key, 0, kPad, 0, key.length);
        }
        for (int i = 0; i < blockSize; i++) {
            ipad[i] = (byte) (kPad[i] ^ 0x36);
            opad[i] = (byte) (kPad[i] ^ 0x5c);
        }
        Arrays.fill(kPad, (byte) 0);
        reset();
    }

    @Override
    public void update(byte in) {
        digest.update(in);
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        digest.update(in, inOff, len);
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        byte[] innerHash = new byte[digest.getDigestResultSize()];
        digest.doFinal(innerHash, 0); // digest 当前含 ipad‖msg

        digest.reset();
        digest.update(opad, 0, blockSize);
        digest.update(innerHash, 0, innerHash.length);
        Arrays.fill(innerHash, (byte) 0);
        int macSize = digest.doFinal(out, outOff);

        reset();
        return macSize;
    }

    @Override
    public void reset() {
        digest.reset();
        digest.update(ipad, 0, blockSize);
    }

    @Override
    public AlgorithmFactory<? extends Mac> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<Mac> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("HMac", "HMAC");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[]{Digest.class};
        }

        @Override
        public Mac construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            CheckUtil.mustTrue(components.length >= 1 && components[0] instanceof Digest,
                    "HMAC 需要注入底层 Digest 组件");
            return new HMac((Digest) components[0]);
        }
    };
}
