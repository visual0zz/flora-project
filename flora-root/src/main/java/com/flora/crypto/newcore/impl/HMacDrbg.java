package com.flora.crypto.newcore.impl;

import com.flora.tag.ThreadFragile;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.bridge.SecureRandomEntropySource;
import com.flora.crypto.newcore.interfaces.algorithm.DeterministicRandomBitGenerator;
import com.flora.crypto.newcore.interfaces.algorithm.EntropySource;
import com.flora.crypto.newcore.interfaces.algorithm.Mac;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;
import com.flora.crypto.newcore.interfaces.material.param.KeyParameterImpl;
import com.flora.java.CheckUtil;

import java.util.Arrays;
import java.util.Set;

/**
 * NIST SP800-90A HMAC_DRBG 的纯 Java 实现（以任意 {@link Mac}（通常 HMAC）为原语）。
 * <p>算法严格按 SP800-90A §10.1.2：以 MAC(K, V) 迭代展开并用 Update 过程（0x00/0x01 分隔符）
 * 在实例化、重播种时混入种子材料。本实现适配 newcore 的 {@link DeterministicRandomBitGenerator}
 * 接口（{@code generate}/{@code reseed} 不含附加输入参数），生成阶段的附加输入分隔符路径保留
 * 供内部 Update 使用。</p>
 * <p>提供两种构造：{@link #HMacDrbg(Mac, EntropySource, int, byte[])} 由熵源实时取熵（生产用）；
 * {@link #HMacDrbg(Mac, byte[], byte[], byte[])} 直接指定熵/nonce（测试可复现用）。</p>
 */
@ThreadFragile
public final class HMacDrbg implements DeterministicRandomBitGenerator {

    private static final long MAX_BITS_REQUEST = 1L << 19;   // 单次请求上限 2^19 bits
    private static final long RESEED_INTERVAL = 1L << 48;   // 重播种前最大生成次数

    private final Mac hmac;
    private final int outLen;                 // MAC 输出字节数
    private final EntropySource entropySource;
    private final int securityStrengthBits;
    private final byte[] personalizationString;

    @Override
    public String getAlgorithmName() {
        return "HMAC_DRBG-" + hmac.getAlgorithmName();
    }

    private byte[] k;
    private byte[] v;
    private long reseedCounter;

    /** 生产用：由熵源实时取熵与 nonce。 */
    public HMacDrbg(Mac hmac, EntropySource entropySource, int securityStrengthBits, byte[] personalizationString) {
        CheckUtil.notNull(hmac, "HMAC 引擎不能为空");
        CheckUtil.notNull(entropySource, "熵源不能为空");
        this.hmac = hmac;
        this.outLen = hmac.getMacSize();
        this.entropySource = entropySource;
        this.securityStrengthBits = securityStrengthBits > 0 ? securityStrengthBits : outLen * 8;
        this.personalizationString = personalizationString != null ? personalizationString.clone() : null;
        byte[] entropy = requireBits(entropySource.getEntropy(this.securityStrengthBits), this.securityStrengthBits);
        byte[] nonce = requireBits(entropySource.getEntropy(Math.max(this.securityStrengthBits / 2, 8)), this.securityStrengthBits / 2);
        instantiate(entropy, nonce, this.personalizationString);
    }

    /** 测试用：直接给定熵与 nonce，结果可复现。 */
    public HMacDrbg(Mac hmac, byte[] entropy, byte[] nonce, byte[] personalizationString) {
        CheckUtil.notNull(hmac, "HMAC 引擎不能为空");
        CheckUtil.notNull(entropy, "熵不能为空");
        CheckUtil.notNull(nonce, "nonce 不能为空");
        this.hmac = hmac;
        this.outLen = hmac.getMacSize();
        this.entropySource = null;
        this.securityStrengthBits = entropy.length * 8;
        this.personalizationString = personalizationString != null ? personalizationString.clone() : null;
        instantiate(entropy, nonce, this.personalizationString);
    }

    private static byte[] requireBits(byte[] in, int bits) {
        if (in == null || in.length * 8 < bits) {
            throw new IllegalArgumentException("熵长度不足");
        }
        return in;
    }

    // ── SP800-90A HMAC_DRBG_Instantiate ──
    private void instantiate(byte[] entropy, byte[] nonce, byte[] personalization) {
        byte[] seedMaterial = concat(entropy, nonce, personalization);
        k = new byte[outLen];                 // 全 0
        v = new byte[outLen];
        Arrays.fill(v, (byte) 1);             // 全 1
        update(seedMaterial);
        reseedCounter = 1;
    }

    // ── HMAC_DRBG_Update ──
    private void update(byte[] providedData) {
        hmac.init(new KeyParameterImpl(k));
        hmac.update(v, 0, v.length);
        hmac.update((byte) 0x00);
        if (providedData != null) {
            hmac.update(providedData, 0, providedData.length);
        }
        byte[] newK = new byte[outLen];
        hmac.doFinal(newK, 0);
        k = newK;

        hmac.init(new KeyParameterImpl(k));
        hmac.update(v, 0, v.length);
        byte[] newV = new byte[outLen];
        hmac.doFinal(newV, 0);
        v = newV;

        if (providedData != null) {
            hmac.init(new KeyParameterImpl(k));
            hmac.update(v, 0, v.length);
            hmac.update((byte) 0x01);
            hmac.update(providedData, 0, providedData.length);
            newK = new byte[outLen];
            hmac.doFinal(newK, 0);
            k = newK;

            hmac.init(new KeyParameterImpl(k));
            hmac.update(v, 0, v.length);
            newV = new byte[outLen];
            hmac.doFinal(newV, 0);
            v = newV;
        }
    }

    // ── SP800-90A HMAC_DRBG_Generate ──
    @Override
    public int generate(byte[] output) {
        CheckUtil.notNull(output, "输出缓冲区不能为空");
        if ((long) output.length * 8 > MAX_BITS_REQUEST) {
            throw new IllegalArgumentException("单次请求比特数超过上限 2^19");
        }
        if (reseedCounter > RESEED_INTERVAL) {
            return -1;                         // 需要 reseed
        }
        // temp = V || V || ... 直至足够
        byte[] temp = new byte[0];
        while (temp.length < output.length) {
            hmac.init(new KeyParameterImpl(k));
            hmac.update(v, 0, v.length);
            byte[] newV = new byte[outLen];
            hmac.doFinal(newV, 0);
            v = newV;
            byte[] bigger = new byte[temp.length + v.length];
            System.arraycopy(temp, 0, bigger, 0, temp.length);
            System.arraycopy(v, 0, bigger, temp.length, v.length);
            temp = bigger;
        }
        System.arraycopy(temp, 0, output, 0, output.length);
        reseedCounter++;
        return output.length * 8;
    }

    @Override
    public int getBlockSize() {
        return outLen;
    }

    // ── SP800-90A HMAC_DRBG_Reseed ──
    @Override
    public void reseed(byte[] additionalInput) {
        byte[] entropy;
        if (entropySource != null) {
            entropy = requireBits(entropySource.getEntropy(securityStrengthBits), securityStrengthBits);
        } else {
            throw new IllegalStateException("该 DRBG 以固定熵构建，不支持 reseed");
        }
        byte[] seedMaterial = concat(entropy, additionalInput, null);
        update(seedMaterial);
        reseedCounter = 1;
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        int len = (a == null ? 0 : a.length) + (b == null ? 0 : b.length) + (c == null ? 0 : c.length);
        byte[] out = new byte[len];
        int off = 0;
        if (a != null) {
            System.arraycopy(a, 0, out, off, a.length);
            off += a.length;
        }
        if (b != null) {
            System.arraycopy(b, 0, out, off, b.length);
            off += b.length;
        }
        if (c != null) {
            System.arraycopy(c, 0, out, off, c.length);
        }
        return out;
    }

    @Override
    public AlgorithmFactory<? extends DeterministicRandomBitGenerator> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<DeterministicRandomBitGenerator> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("HMAC_DRBG");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<? extends AlgorithmComponent>[] componentTypes() {
            return new Class[]{Mac.class};
        }

        @Override
        public DeterministicRandomBitGenerator construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            CheckUtil.mustTrue(components.length >= 1, "HMAC_DRBG 需要传入 Mac 组件");
            Mac mac = (Mac) components[0];
            int strength = mac.getMacSize() * 8;
            return new HMacDrbg(mac, new SecureRandomEntropySource(), strength, null);
        }
    };
}
