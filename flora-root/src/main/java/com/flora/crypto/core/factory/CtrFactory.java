package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.mode.SICBlockCipher;

import java.util.Set;

/** CTR 分组密码模式工厂（以底层 {@link BlockCipher} 为参数的组合算法，实现为 SIC）。 */
public final class CtrFactory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("CTR");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[]{BlockCipher.class};
    }

    @Override
    public Object create(Object[] args) {
        return new SICBlockCipher((BlockCipher) args[0]);
    }
}
