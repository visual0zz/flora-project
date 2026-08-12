package com.flora.crypto.core.impl;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.interfaces.algorithm.DerivationFunction;
import com.flora.crypto.core.interfaces.material.param.DerivationParameter;
import com.flora.java.CheckUtil;

import java.util.Set;

/**
 * 派生函数（KDF）的最简占位实现。
 * <p>项目已随附多个纯 Java 真实实现（KDF2 / HKDF / PBKDF2 / scrypt / bcrypt / Argon2），
 * 可按名注册后使用。本类仅作未实现算法的兜底。</p>
 */
public final class PlaceholderDerivationFunction implements DerivationFunction {

    @Override
    public String getAlgorithmName() {
        return "placeholder";
    }

    @Override
    public void init(DerivationParameter params) {
        // 占位：不保存参数
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        // 占位：丢弃输入
    }

    @Override
    public int generateBytes(byte[] out, int outOff, int len) {
        throw new UnsupportedOperationException(
                "PlaceholderDerivationFunction 为占位实现，请注册真实 KDF（如 KDF2 / HKDF）");
    }

    @Override
    public AlgorithmFactory<? extends DerivationFunction> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<DerivationFunction> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("PlaceholderDerivationFunction", "placeholder-kdf");
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
        public DerivationFunction construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return new PlaceholderDerivationFunction();
        }
    };
}
