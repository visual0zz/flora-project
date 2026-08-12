package com.flora.crypto.core.impl;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.interfaces.algorithm.ExtendableOutputFunction;
import com.flora.java.CheckUtil;

import java.util.Set;

/**
 * XOF（可变长输出）的最简占位实现。
 * <p>待接入真实引擎（如 SHAKE128/256）后，通过注册覆盖即可。</p>
 */
public final class PlaceholderXof implements ExtendableOutputFunction {

    @Override
    public String getAlgorithmName() {
        return "PLACEHOLDER_XOF";
    }

    @Override
    public int getDigestResultSize() {
        return 0;
    }

    @Override
    public int getInternalBlockLength() {
        return 0;
    }

    @Override
    public void update(byte in) {
        // 占位：丢弃输入
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        // 占位：丢弃输入
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        throw new UnsupportedOperationException(
                "PlaceholderXof 为占位实现，请注册真实 XOF 引擎（如 SHAKE）");
    }

    @Override
    public void reset() {
        // 占位：无状态
    }

    @Override
    public int doFinal(byte[] out, int outOff, int outLen) {
        throw new UnsupportedOperationException(
                "PlaceholderXof 为占位实现，请注册真实 XOF 引擎（如 SHAKE）");
    }

    @Override
    public int doOutput(byte[] out, int outOff, int outLen) {
        throw new UnsupportedOperationException(
                "PlaceholderXof 为占位实现，请注册真实 XOF 引擎（如 SHAKE）");
    }

    @Override
    public AlgorithmFactory<? extends ExtendableOutputFunction> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<ExtendableOutputFunction> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("PlaceholderXof", "placeholder-xof");
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
        public ExtendableOutputFunction construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return new PlaceholderXof();
        }
    };
}
