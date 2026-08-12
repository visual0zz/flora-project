package com.flora.crypto.core.padding;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.core.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.core.interfaces.algorithm.Digest;
import com.flora.crypto.core.interfaces.algorithm.MaskGenerationFunction;
import com.flora.java.CheckUtil;

import java.util.Set;

/**
 * MGF1 掩码生成函数（RFC 8017 §B.2.1）。
 * <p>MGF1(seed, maskLen) = T0‖T1‖...‖T(n-1)，Ti = H(seed ‖ I2OSP(i, 4))，
 * 循环直至累计长度 ≥ maskLen，截取前 maskLen 字节。</p>
 */
public final class Mgf1Generator implements MaskGenerationFunction {

    private final Digest digest;

    public Mgf1Generator(Digest digest) {
        CheckUtil.notNull(digest, "摘要不能为空");
        this.digest = digest;
    }

    @Override
    public void generateMask(byte[] seed, int seedOff, int seedLen,
                             byte[] out, int outOff, int length) {
        CheckUtil.notNull(seed, "种子不能为空");
        CheckUtil.notNull(out, "输出不能为空");
        if (length > (long) digest.getDigestResultSize() * 0x100000000L) {
            throw new IllegalArgumentException("maskLen 超出 MGF1 上限");
        }
        byte[] counter = new byte[4];
        byte[] hash = new byte[digest.getDigestResultSize()];
        int pos = outOff;
        int end = outOff + length;
        int i = 0;
        while (pos < end) {
            counter[0] = (byte) (i >>> 24);
            counter[1] = (byte) (i >>> 16);
            counter[2] = (byte) (i >>> 8);
            counter[3] = (byte) i;
            digest.reset();
            digest.update(seed, seedOff, seedLen);
            digest.update(counter, 0, 4);
            digest.doFinal(hash, 0);
            int chunk = Math.min(hash.length, end - pos);
            System.arraycopy(hash, 0, out, pos, chunk);
            pos += chunk;
            i++;
        }
    }

    @Override
    public String getAlgorithmName() {
        return "MGF1";
    }

    @Override
    public AlgorithmFactory<? extends MaskGenerationFunction> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<MaskGenerationFunction> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("MGF1");
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
        public MaskGenerationFunction construct(String algorithmName, AlgorithmComponent... components) {
            return new Mgf1Generator((Digest) components[0]);
        }
    };
}
