package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.AgreementBasedKem;

import java.util.Set;

/**
 * 基于「密钥协商 + KDF」的真实 KEM 工厂（经典曲线 ECDH / X25519 / DH 等）。
 * <p>为每个支持的协商名创建一个携带名字的实例；具体度覆写为支持集合大小。</p>
 */
public final class AgreementBasedKemFactory implements AlgorithmFactory {

    private String name;

    @Override
    public void chooseAlgorithm(String name) {
        this.name = name;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return AgreementBasedKem.SUPPORTED;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return AgreementBasedKem.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return AgreementBasedKem.of(name);
    }
}
