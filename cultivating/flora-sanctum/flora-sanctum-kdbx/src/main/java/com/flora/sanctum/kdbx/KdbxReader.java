package com.flora.sanctum.kdbx;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;
import com.flora.sanctum.kdbx.internal.KdbxParser;

/**
 * KDBX 读取入口（仅负责解密与读取，输出通用模型 {@link KdbxDocument}）。
 * <p>本类型不依赖任何上层/Sanctum 类型，可独立复用于任意需要读取 KeePass / KeePassXC 仓库的场景。
 * 解析期的诊断信息（如自定义图标解码失败）仅通过外部注入的 {@link Logger} 记录，本类不配置任何日志路径。</p>
 */
public final class KdbxReader {

    private KdbxReader() {
    }

    /** 读取并解密 KDBX；无密钥文件。 */
    public static KdbxDocument read(byte[] data, char[] password) throws KdbxReadException {
        return read(data, password, null);
    }

    /**
     * 读取并解密 KDBX（支持主密码与可选密钥文件）；默认静默日志。
     *
     * @param data       文件全部字节
     * @param password   主密码（可为空数组，表示仅用密钥文件）
     * @param keyFile    密钥文件字节（可为 null）
     * @throws KdbxReadException 解密/解析失败时携带结构化上下文
     */
    public static KdbxDocument read(byte[] data, char[] password, byte[] keyFile) throws KdbxReadException {
        return read(data, password, keyFile, LoggerFactory.noOp());
    }

    /**
     * 读取并解密 KDBX（支持主密码与可选密钥文件），并经由外部注入的 {@link Logger} 记录解析诊断。
     *
     * @param data       文件全部字节
     * @param password   主密码（可为空数组，表示仅用密钥文件）
     * @param keyFile    密钥文件字节（可为 null）
     * @param log        外部注入的日志器（用于记录解析期诊断）；不配置路径，仅记录
     * @throws KdbxReadException 解密/解析失败时携带结构化上下文
     */
    public static KdbxDocument read(byte[] data, char[] password, byte[] keyFile, Logger log) throws KdbxReadException {
        return KdbxParser.parse(data, password, keyFile, log);
    }

    /**
     * 仅校验主密码/密钥文件是否正确（等价于完整读取，但调用方仅关心能否通过认证）。
     *
     * @throws KdbxReadException 密码错误或文件损坏时抛出
     */
    public static void verify(byte[] data, char[] password, byte[] keyFile) throws KdbxReadException {
        read(data, password, keyFile);
    }
}
