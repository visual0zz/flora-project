package com.flora.crypto.newcore.padding;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.interfaces.algorithm.Padding;

import java.security.SecureRandom;
import java.util.Set;

/**
 * PKCS#7 填充（PKCS#5 为其块大小 8 的特例）。
 * <p>在 {@code in[inOff]} 起填充至块对齐，每个填充字节的值等于填充长度。</p>
 */
public final class PKCS7Padding implements Padding {

    @Override
    public void init(SecureRandom random) {
        // PKCS7 不需要随机数
    }

    @Override
    public String getPaddingName() {
        return "PKCS7";
    }

    @Override
    public String getAlgorithmName() {
        return "PKCS7";
    }

    @Override
    public int getBlockSize() {
        return 0; // 变长填充，块大小由使用方（分组密码）决定
    }

    @Override
    public int addPadding(byte[] in, int inOff) {
        int count = in.length - inOff;
        byte code = (byte) count;
        while (inOff < in.length) {
            in[inOff++] = code;
        }
        return count;
    }

    @Override
    public int padCount(byte[] in, int inOff) {
        int count = in[in.length - 1] & 0xFF;
        if (count < 1 || count > in.length - inOff) {
            throw new IllegalStateException("PKCS7 填充非法: 长度=" + count);
        }
        for (int i = in.length - count; i < in.length - 1; i++) {
            if ((in[i] & 0xFF) != count) {
                throw new IllegalStateException("PKCS7 填充非法");
            }
        }
        return count;
    }

    @Override
    public AlgorithmFactory<? extends Padding> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<Padding> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("PKCS7");
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
        public Padding construct(String algorithmName, AlgorithmComponent... components) {
            return new PKCS7Padding();
        }
    };
}
