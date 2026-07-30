package com.flora.os.keyring;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Keyring 集成测试。只在当前平台有原生密钥链工具时实际运行。
 */
class KeyringTest {

    @Test
    void setGetDeleteCycle() throws Exception {
        if (!KeyringProvider.isAvailable()) {
            System.out.println("跳过 keyring 测试：当前平台未安装原生工具 ("
                    + KeyringProvider.storageType() + ")");
            return;
        }

        String domain = "flora-test-" + System.currentTimeMillis();
        String account = "testuser";
        String password = "s3cret-" + System.nanoTime();

        try (Keyring keyring = KeyringProvider.create()) {
            keyring.setPassword(domain, account, password);
            String retrieved = keyring.getPassword(domain, account);
            assertEquals(password, retrieved);

            keyring.deletePassword(domain, account);
            assertThrows(KeyringException.class, () -> keyring.getPassword(domain, account));
        }
    }

    @Test
    void storageType() {
        assertNotNull(KeyringProvider.storageType());
        System.out.println("当前平台 keystore: " + KeyringProvider.storageType());
    }
}
