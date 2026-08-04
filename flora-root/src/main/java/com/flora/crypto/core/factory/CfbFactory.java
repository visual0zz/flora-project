package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.mode.CFBBlockCipher;

import java.util.Set;

/** CFB 分组密码模式工厂（以底层 {@link BlockCipher} 为参数的组合算法）。 */
public final class CfbFactory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("CFB");
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
        return new CFBBlockCipher((BlockCipher) args[0]);
    }
}
