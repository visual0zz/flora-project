package com.flora.os.keyring;

/**
 * 跨平台操作系统密钥存储接口。
 * <p>提供统一 API 存储/读取/删除密码，委托给各平台的本地密钥链实现。
 * 零外部依赖，通过平台 CLI 工具交互。</p>
 */
public interface Keyring extends AutoCloseable {

    /** 存储密码。如果已存在则覆盖。 */
    void setPassword(String domain, String account, String password) throws KeyringException;

    /** 读取密码。不存在时抛 {@link KeyringException}。 */
    String getPassword(String domain, String account) throws KeyringException;

    /** 删除密码。不存在时静默忽略。 */
    void deletePassword(String domain, String account) throws KeyringException;

    /** 返回当前使用的密钥存储类型描述。 */
    String getStorageType();

    @Override void close() throws KeyringException;
}
