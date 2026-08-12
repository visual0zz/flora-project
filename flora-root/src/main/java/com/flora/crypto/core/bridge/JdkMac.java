package com.flora.crypto.core.bridge;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.core.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.crypto.core.interfaces.material.param.KeyParameter;
import com.flora.java.CheckUtil;
import com.flora.tag.ThreadFragile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * 把 JDK 自带的 {@link Mac} 接入 newcore 的 {@code Mac} 接口。
 * <p>示例：{@code CryptoProvider.mac("HmacSHA256")}。</p>
 * <p>注意：本类字段名 {@code mac} 指 JDK 的 {@link Mac}，与 newcore 接口名冲突，
 * 故接口一律用完全限定名 {@code com.flora.crypto.newcore.interfaces.algorithm.Mac} 引用。</p>
 */
@ThreadFragile
public final class JdkMac implements com.flora.crypto.core.interfaces.algorithm.Mac {

    private final String algorithm;
    private final Mac mac;

    private JdkMac(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.mac = Mac.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的 MAC 算法: " + algorithm, e);
        }
    }

    public static JdkMac of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkMac(algorithm);
    }

    public static final Set<String> SUPPORTED = Set.of(
            "HmacMD5", "HmacSHA1", "HmacSHA224", "HmacSHA256", "HmacSHA384", "HmacSHA512");

    @Override
    public void init(CipherParameter params) {
        CheckUtil.notNull(params, "参数不能为空");
        if (!(params instanceof KeyParameter)) {
            throw new IllegalArgumentException("需要 KeyParameter（对称密钥）");
        }
        try {
            mac.init(new SecretKeySpec(((KeyParameter) params).getKey(), algorithm));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化 MAC 失败: " + algorithm, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public int getMacSize() {
        return mac.getMacLength();
    }

    @Override
    public void update(byte in) {
        mac.update(in);
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        mac.update(in, inOff, len);
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        byte[] result = mac.doFinal();
        System.arraycopy(result, 0, out, outOff, result.length);
        return result.length;
    }

    @Override
    public void reset() {
        mac.reset();
    }

    @Override
    public AlgorithmFactory<? extends com.flora.crypto.core.interfaces.algorithm.Mac> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<com.flora.crypto.core.interfaces.algorithm.Mac> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return SUPPORTED;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[0];
        }

        @Override
        public com.flora.crypto.core.interfaces.algorithm.Mac construct(
                String algorithmName, AlgorithmComponent... components) {
            return JdkMac.of(algorithmName);
        }
    };
}
