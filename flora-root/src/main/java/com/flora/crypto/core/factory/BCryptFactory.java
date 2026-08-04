package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.BCrypt;

import java.util.Set;

/** BCrypt 口令哈希 / KDF 工厂（大小写别名）。 */
public final class BCryptFactory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("BCrypt", "BCRYPT");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return new BCrypt();
    }
}
