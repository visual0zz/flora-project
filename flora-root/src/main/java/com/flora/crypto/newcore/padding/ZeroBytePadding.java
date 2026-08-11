package com.flora.crypto.newcore.padding;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.interfaces.algorithm.Padding;

import java.security.SecureRandom;
import java.util.Set;

/**
 * 零字节填充（末尾补 {@code 0x00} 至块对齐）。
 * <p>注意：零填充无法无歧义地还原原始长度（末尾本来就是 0 的情况），
 * 仅适合已知明文长度或定长字段的场景。</p>
 */
public final class ZeroBytePadding implements Padding {

    @Override
    public void init(SecureRandom random) {
        // 不需要随机数
    }

    @Override
    public String getPaddingName() {
        return "ZeroByte";
    }

    @Override
    public String getAlgorithmName() {
        return "ZeroByte";
    }

    @Override
    public int getBlockSize() {
        return 0; // 变长填充，块大小由使用方（分组密码）决定
    }

    @Override
    public int addPadding(byte[] in, int inOff) {
        int count = 0;
        while (inOff < in.length) {
            in[inOff++] = 0;
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
            return Set.of("ZeroByte");
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
            return new ZeroBytePadding();
        }
    };
}
