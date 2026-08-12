package com.flora.crypto.core.impl;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.core.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.core.interfaces.algorithm.DerivationFunction;
import com.flora.crypto.core.interfaces.algorithm.Mac;
import com.flora.crypto.core.interfaces.material.param.DerivationParameter;
import com.flora.crypto.core.interfaces.material.param.KeyParameterImpl;
import com.flora.crypto.core.param.HkdfParameters;
import com.flora.java.CheckUtil;

import java.util.Set;

/**
 * HKDF-Expand（RFC 5869）派生函数，纯 Java 实现，不依赖 JDK。
 * <p>以任意 newcore {@link Mac}（通常 HMAC）为原语：T(0)=空，T(i)=HMAC(PRK, T(i-1) || info || i)，
 * 循环拼接直至得到所需长度。</p>
 */
public final class HkdfDerivationFunction implements DerivationFunction {

    private final Mac mac;
    private final int macSize;
    private byte[] prk;
    private byte[] info;

    public HkdfDerivationFunction(Mac mac) {
        CheckUtil.notNull(mac, "MAC 引擎不能为空");
        this.mac = mac;
        this.macSize = mac.getMacSize();
    }

    @Override
    public String getAlgorithmName() {
        return "HKDF";
    }

    @Override
    public void init(DerivationParameter params) {
        CheckUtil.notNull(params, "参数不能为空");
        if (!(params instanceof HkdfParameters)) {
            throw new IllegalArgumentException("HKDF 需要 HkdfParameters");
        }
        HkdfParameters p = (HkdfParameters) params;
        this.prk = p.getKey();
        this.info = p.getInfo();
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        if (info == null) {
            info = new byte[len];
            System.arraycopy(in, inOff, info, 0, len);
        } else {
            byte[] merged = new byte[info.length + len];
            System.arraycopy(info, 0, merged, 0, info.length);
            System.arraycopy(in, inOff, merged, info.length, len);
            info = merged;
        }
    }

    @Override
    public int generateBytes(byte[] out, int outOff, int len) {
        CheckUtil.notNull(out, "输出缓冲区不能为空");
        CheckUtil.mustTrue(len > 0, "派生长度必须大于 0");
        byte[] t = new byte[0];
        int counter = 1;
        int written = 0;
        while (written < len) {
            mac.init(new KeyParameterImpl(prk));
            mac.update(t, 0, t.length);
            if (info != null) {
                mac.update(info, 0, info.length);
            }
            mac.update((byte) counter);
            byte[] block = new byte[macSize];
            mac.doFinal(block, 0);
            int toCopy = Math.min(macSize, len - written);
            System.arraycopy(block, 0, out, outOff + written, toCopy);
            written += toCopy;
            t = block;
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
            return Set.of("HKDF", "Hkdf");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[]{Mac.class};
        }

        @Override
        public DerivationFunction construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            CheckUtil.mustTrue(components.length >= 1 && components[0] instanceof Mac,
                    "HKDF 需要注入底层 Mac 组件");
            return new HkdfDerivationFunction((Mac) components[0]);
        }
    };
}
