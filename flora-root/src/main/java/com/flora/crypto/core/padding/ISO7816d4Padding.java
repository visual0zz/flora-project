package com.flora.crypto.core.padding;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.interfaces.algorithm.Padding;

import java.security.SecureRandom;
import java.util.Set;

/**
 * ISO 7816-4 填充（先填一个 {@code 0x80}，其后补 {@code 0x00} 至块对齐）。
 */
public final class ISO7816d4Padding implements Padding {

    @Override
    public void init(SecureRandom random) {
        // 不需要随机数
    }

    @Override
    public String getAlgorithmName() {
        return "ISO7816-4";
    }

    @Override
    public int getBlockSize() {
        return 0; // 变长填充，块大小由使用方（分组密码）决定
    }

    @Override
    public int addPadding(byte[] in, int inOff) {
        in[inOff] = (byte) 0x80;
        int count = 1;
        for (int i = inOff + 1; i < in.length; i++) {
            in[i] = 0;
            count++;
        }
        return count;
    }

    @Override
    public int padCount(byte[] in, int inOff) {
        int count = 0;
        int i = in.length - 1;
        while (i >= inOff && in[i] == 0) {
            i--;
            count++;
        }
        if (i < inOff || in[i] != (byte) 0x80) {
            throw new IllegalStateException("ISO7816-4 填充非法");
        }
        return count + 1;
    }

    @Override
    public AlgorithmFactory<? extends Padding> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<Padding> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("ISO7816-4");
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
        public Padding construct(String algorithmName, AlgorithmComponent... components) {
            return new ISO7816d4Padding();
        }
    };
}
