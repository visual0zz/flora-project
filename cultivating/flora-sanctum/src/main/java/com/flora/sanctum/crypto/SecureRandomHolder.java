package com.flora.sanctum.crypto;

import java.security.SecureRandom;

/**
 * Thread-safe {@link SecureRandom} holder.
 * <p>
 * Uses the default constructor which provides a self-seeded {@code SecureRandom}
 * (on Linux this typically reads from {@code /dev/urandom}).
 */
public final class SecureRandomHolder {

    private static final SecureRandom INSTANCE = new SecureRandom();

    private SecureRandomHolder() {
    }

    /**
     * Returns the shared {@link SecureRandom} instance.
     */
    public static SecureRandom get() {
        return INSTANCE;
    }
}
