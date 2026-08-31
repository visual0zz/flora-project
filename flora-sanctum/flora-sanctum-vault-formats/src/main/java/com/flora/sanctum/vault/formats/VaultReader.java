package com.flora.sanctum.vault.formats;

import com.flora.sanctum.kdbx.KdbxDocument;

/**
 * 单一保险库格式的只读读取器。
 * <p>统一以「文件全部字节 + 主密码 + 可选密钥文件」为输入，输出通用模型
 * {@link KdbxDocument}（与 KDBX 读取结果同构，便于上层统一映射）。</p>
 * <p>OPVault / 1PUX 这类目录型格式以 ZIP 字节传入（单 {@code byte[]} 统一 API）。</p>
 */
public interface VaultReader {

    /** 本读取器对应的格式。 */
    VaultFormat format();

    /**
     * 读取并解密保险库。
     *
     * @param data     文件全部字节（OPVault/1PUX 为对应 ZIP 的字节）
     * @param password 主密码（可为空数组，表示仅用密钥文件）
     * @param keyFile  密钥文件字节（可为 null）
     * @return 解密后的通用模型
     * @throws VaultReadException 解密/解析失败时携带结构化上下文
     */
    KdbxDocument read(byte[] data, char[] password, byte[] keyFile) throws VaultReadException;
}
