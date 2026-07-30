package com.flora.os.keyring;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Keyring 集成测试。只在当前平台有原生密钥链支持时实际运行。
 */
class KeyringTest {

    @Test
    void setGetDeleteCycle() {
        if (!KeyringProvider.isSupported()) {
            System.out.println("跳过 keyring 测试：当前平台不支持 (" + System.getProperty("os.name") + ")");
            return;
        }

        String domain = "flora-test-" + System.currentTimeMillis();
        String account = "testuser";
        String password = "s3cret-" + System.nanoTime();

        try (Keyring keyring = KeyringProvider.create()) {
            // 写入
            keyring.setPassword(domain, account, password);
            System.out.println("已写入: " + keyring.getStorageType());

            // 读取验证
            String retrieved = keyring.getPassword(domain, account);
            assertEquals(password, retrieved);
            System.out.println("读取成功");

            // 删除
            keyring.deletePassword(domain, account);
            System.out.println("已删除");

            // 确认删除后读取抛异常
            assertThrows(KeyringException.class, () -> keyring.getPassword(domain, account));
            System.out.println("删除验证成功");

        } catch (KeyringException e) {
            // 原生服务不可用（如 Linux 无 secret-tool）时跳过
            System.out.println("跳过 keyring 测试：" + e.getMessage());
        }
    }

    @Test
    void storageType() {
        assertNotNull(KeyringProvider.storageType());
        System.out.println("当前平台 keystore: " + KeyringProvider.storageType());
    }
}
