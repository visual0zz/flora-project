package com.flora.crypto.core.impl;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.interfaces.algorithm.KeyEncapsulationMechanism;
import com.flora.crypto.core.interfaces.material.kem.Decapsulator;
import com.flora.crypto.core.interfaces.material.kem.Encapsulator;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.java.CheckUtil;

import java.util.Set;

/**
 * KEM 占位实现（对应本项目尚未接入的算法，如后量子 ML-KEM）。
 * <p>封装/解封装一律抛 {@link UnsupportedOperationException}。真实引擎（如 ML-KEM 格密码、
 * 或基于密钥协商的 {@code AgreementBasedKem}）实现后通过注册覆盖即可。</p>
 */
public final class PlaceholderKem implements KeyEncapsulationMechanism {

    @Override
    public String getAlgorithmName() {
        return "placeholder";
    }

    @Override
    public Encapsulator newEncapsulator(AsymmetricPublicKeyParameter publicKey) {
        throw new UnsupportedOperationException(
                "PlaceholderKem 为占位实现，请注册真实 KEM 引擎（如 AgreementBasedKem / ML-KEM）");
    }

    @Override
    public Decapsulator newDecapsulator(AsymmetricPrivateKeyParameter privateKey) {
        throw new UnsupportedOperationException(
                "PlaceholderKem 为占位实现，请注册真实 KEM 引擎（如 AgreementBasedKem / ML-KEM）");
    }

    @Override
    public AlgorithmFactory<? extends KeyEncapsulationMechanism> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<KeyEncapsulationMechanism> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("PlaceholderKem", "placeholder-kem");
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
        public KeyEncapsulationMechanism construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return new PlaceholderKem();
        }
    };
}
