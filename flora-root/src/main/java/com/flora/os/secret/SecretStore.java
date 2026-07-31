package com.flora.os.secret;

/**
 * 跨平台操作系统密钥托管接口。
 * <p>在 OS 最低层级的密钥管理设施中存储/读取/删除密钥。
 * 各平台实现：Linux→内核 keyring，macOS→Security.framework，Windows→Credential Manager（SESSION 级）。
 * 零第三方依赖，仅通过 FFM 调用系统原生 API。</p>
 *
 * <p>此接口非持久化密码管理器——Linux 和 Windows 实现仅存活于当前会话/进程生命周期。</p>
 */
public interface SecretStore extends AutoCloseable {

    /** 存储密钥。如果已存在则覆盖。 */
    void store(String domain, String account, byte[] secret) throws SecretStoreException;

    /** 读取密钥。不存在时抛 {@link SecretStoreException}。 */
    byte[] retrieve(String domain, String account) throws SecretStoreException;

    /** 删除密钥。不存在时静默忽略。 */
    void delete(String domain, String account) throws SecretStoreException;

    /** 返回当前使用的密钥存储类型描述。 */
    String getProvider();

    @Override void close() throws SecretStoreException;
}
