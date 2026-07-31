package com.flora.os.secret;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

/**
 * SecretStore 集成测试。只在当前平台密钥存储可用时实际运行。
 */
class SecretStoreTest {

    @Test
    void storeRetrieveDeleteCycle() throws Exception {
        if (!SecretStoreProvider.isAvailable()) {
            System.out.println("跳过秘密存储测试：当前平台不可用 ("
                    + SecretStoreProvider.getProviderName() + ")");
            return;
        }

        String domain = "flora-test-" + System.currentTimeMillis();
        String account = "testuser";
        byte[] secret = ("s3cret-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);

        try (SecretStore store = SecretStoreProvider.create()) {
            store.store(domain, account, secret);
            byte[] retrieved = store.retrieve(domain, account);
            assertArrayEquals(secret, retrieved);

            store.delete(domain, account);
            assertThrows(SecretStoreException.class, () -> store.retrieve(domain, account));
        }
    }

    @Test
    void providerName() {
        assertNotNull(SecretStoreProvider.getProviderName());
        System.out.println("当前平台: " + SecretStoreProvider.getProviderName());
    }
}
