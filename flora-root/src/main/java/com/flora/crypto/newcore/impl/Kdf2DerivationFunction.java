package com.flora.crypto.newcore.impl;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.interfaces.algorithm.DerivationFunction;
import com.flora.crypto.newcore.interfaces.algorithm.Digest;
import com.flora.crypto.newcore.interfaces.material.param.DerivationParameter;
import com.flora.crypto.newcore.param.KdfParameters;
import com.flora.java.CheckUtil;

import java.util.Arrays;
import java.util.Set;

/**
 * KDF2（ISO 18033-2）派生函数，纯 Java 实现，不依赖 JDK。
 * <p>以任意 newcore {@link Digest} 为原语：K(i) = HASH(Z || Counter(4 字节大端) [|| sharedInfo])，
 * 计数器从 1 开始，循环拼接直至得到所需长度。</p>
 */
public final class Kdf2DerivationFunction implements DerivationFunction {

    private final Digest digest;
    private final int hLen;
    private byte[] shared;
    private byte[] sharedInfo;

    public Kdf2DerivationFunction(Digest digest) {
        CheckUtil.notNull(digest, "摘要引擎不能为空");
        this.digest = digest;
        this.hLen = digest.getDigestResultSize();
    }

    @Override
    public String getAlgorithmName() {
        return "KDF2";
    }

    @Override
    public void init(DerivationParameter params) {
        CheckUtil.notNull(params, "参数不能为空");
        if (!(params instanceof KdfParameters)) {
            throw new IllegalArgumentException("KDF2 需要 KdfParameters");
        }
        KdfParameters p = (KdfParameters) params;
        this.shared = p.getShared();
        this.sharedInfo = p.getIV();
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        // KDF2 的共享信息在 init 时确定；此处允许追加更多信息
        if (sharedInfo == null) {
            sharedInfo = new byte[len];
            System.arraycopy(in, inOff, sharedInfo, 0, len);
        } else {
            byte[] merged = new byte[sharedInfo.length + len];
            System.arraycopy(sharedInfo, 0, merged, 0, sharedInfo.length);
            System.arraycopy(in, inOff, merged, sharedInfo.length, len);
            sharedInfo = merged;
        }
    }

    @Override
    public int generateBytes(byte[] out, int outOff, int len) {
        CheckUtil.notNull(out, "输出缓冲区不能为空");
        CheckUtil.mustTrue(len > 0, "派生长度必须大于 0");
        byte[] k = new byte[hLen];
        int counter = 1;
        int written = 0;
        while (written < len) {
            digest.reset();
            digest.update(shared, 0, shared.length);
            digest.update((byte) (counter >>> 24));
            digest.update((byte) (counter >>> 16));
            digest.update((byte) (counter >>> 8));
            digest.update((byte) counter);
            if (sharedInfo != null) {
                digest.update(sharedInfo, 0, sharedInfo.length);
            }
            digest.doFinal(k, 0);
            int toCopy = Math.min(hLen, len - written);
            System.arraycopy(k, 0, out, outOff + written, toCopy);
            written += toCopy;
            counter++;
        }
        return len;
    }

    @Override
    public AlgorithmFactory<? extends DerivationFunction> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<DerivationFunction> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("KDF2", "Kdf2");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<? extends AlgorithmComponent>[] componentTypes() {
            return new Class[]{Digest.class};
        }

        @Override
        public DerivationFunction construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            CheckUtil.mustTrue(components.length >= 1 && components[0] instanceof Digest,
                    "KDF2 需要注入底层 Digest 组件");
            return new Kdf2DerivationFunction((Digest) components[0]);
        }
    };
}
