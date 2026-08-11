package com.flora.crypto.newcore.padding;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.interfaces.algorithm.AsymmetricScheme;
import com.flora.crypto.newcore.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.newcore.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;
import com.flora.java.CheckUtil;

import java.security.SecureRandom;
import java.util.Set;

/**
 * EME-PKCS1-v1.5 消息编码方案（RFC 8017 §7.2）。
 * <p>编码块结构：{@code 0x00 ‖ 0x02 ‖ PS ‖ 0x00 ‖ M}，PS 为至少 8 字节的随机非零字节。
 * 最小开销 11 字节。块大小（密钥字节长度）在 {@link #init} 时从非对称密钥参数推断。</p>
 */
public final class PKCS1v15Padding implements AsymmetricScheme {

    private final SecureRandom random;
    private int keyBytes = -1;

    public PKCS1v15Padding() {
        this(new SecureRandom());
    }

    public PKCS1v15Padding(SecureRandom random) {
        CheckUtil.notNull(random, "随机源不能为空");
        this.random = random;
    }

    @Override
    public void init(boolean forEncryption, CipherParameter params, SecureRandom random) {
        if (params instanceof AsymmetricPublicKeyParameter pub) {
            this.keyBytes = pub.getPublicKey().length;
        } else if (params instanceof AsymmetricPrivateKeyParameter priv) {
            this.keyBytes = priv.getPrivateKey().length;
        } else {
            throw new IllegalArgumentException(
                    "PKCS1v15 需要非对称密钥参数以推断块大小");
        }
    }

    @Override
    public String getAlgorithmName() {
        return "PKCS1v15";
    }

    @Override
    public int getInputBlockSize() {
        ensureKeyBytes();
        return keyBytes - 11;
    }

    @Override
    public int getOutputBlockSize() {
        ensureKeyBytes();
        return keyBytes;
    }

    private void ensureKeyBytes() {
        if (keyBytes < 0) {
            throw new IllegalStateException("PKCS1v15 尚未初始化（init 未调用）");
        }
    }

    @Override
    public byte[] encode(byte[] in, int inOff, int inLen) {
        ensureKeyBytes();
        CheckUtil.notNull(in, "输入不能为空");
        if (inLen > keyBytes - 11) {
            throw new IllegalArgumentException("明文过长: " + inLen + " > " + (keyBytes - 11));
        }
        byte[] em = new byte[keyBytes];
        em[0] = 0x00;
        em[1] = 0x02;
        int psLen = keyBytes - 3 - inLen;
        byte[] ps = new byte[psLen];
        random.nextBytes(ps);
        for (int i = 0; i < psLen; i++) {
            while (ps[i] == 0) {
                ps[i] = (byte) random.nextInt();
            }
        }
        System.arraycopy(ps, 0, em, 2, psLen);
        em[keyBytes - inLen - 1] = 0x00;
        System.arraycopy(in, inOff, em, keyBytes - inLen, inLen);
        return em;
    }

    @Override
    public byte[] decode(byte[] in, int inOff, int len) {
        ensureKeyBytes();
        CheckUtil.notNull(in, "输入不能为空");
        if (len != keyBytes) {
            throw new IllegalArgumentException("PKCS1v15 块长度不符: " + len + " != " + keyBytes);
        }
        if (in[inOff] != 0x00 || in[inOff + 1] != 0x02) {
            throw new IllegalArgumentException("PKCS1v15 块头非法");
        }
        int sep = -1;
        for (int i = inOff + 2; i < inOff + len; i++) {
            if (in[i] == 0x00) {
                sep = i;
                break;
            }
        }
        if (sep < 0) {
            throw new IllegalArgumentException("PKCS1v15 缺少分隔符");
        }
        int psLen = sep - inOff - 2;
        if (psLen < 8) {
            throw new IllegalArgumentException("PKCS1v15 填充过短");
        }
        for (int i = inOff + 2; i < sep; i++) {
            if (in[i] == 0x00) {
                throw new IllegalArgumentException("PKCS1v15 PS 含零字节");
            }
        }
        int msgLen = inOff + len - sep - 1;
        byte[] m = new byte[msgLen];
        System.arraycopy(in, sep + 1, m, 0, msgLen);
        return m;
    }

    @Override
    public AlgorithmFactory<? extends AsymmetricScheme> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<AsymmetricScheme> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("PKCS1v15");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<? extends AlgorithmComponent>[] componentTypes() {
            return new Class[0];
        }

        @Override
        public AsymmetricScheme construct(String algorithmName, AlgorithmComponent... components) {
            return new PKCS1v15Padding();
        }
    };
}
