package com.flora.sanctum.kdbx;

/**
 * KDBX 读取失败的结构化异常。
 * <p>携带失败阶段（{@link Stage}）、文件主版本、检测到的 cipherId / KDF uuid（均为非敏感的公开标识），
 * 以及底层 cause。绝不携带密钥或明文。</p>
 * <p>库不碰日志：诊断信息通过本异常携带，由调用方决定如何呈现。</p>
 */
public class KdbxReadException extends Exception {

    /** 失败阶段，用于快速定位问题环节。 */
    public enum Stage {
        MAGIC("文件魔数/版本"),
        HEADER("头部字段"),
        HEADER_HASH("头部完整性校验"),
        HEADER_HMAC("头部认证(HMAC)"),
        KDF("密钥派生"),
        DECRYPT("载荷解密"),
        INNER("内层流/内层头"),
        XML("XML 解析"),
        UNKNOWN("未知");

        public final String label;

        Stage(String label) {
            this.label = label;
        }
    }

    private final Stage stage;
    private final Integer majorVersion;
    /** 非敏感：cipherId 的 hex 表示（KeePass 公开 UUID）。 */
    private final String cipherId;
    /** 非敏感：KDF uuid 的 hex 表示（KeePass 公开 UUID）。 */
    private final String kdfUuid;

    public KdbxReadException(Stage stage, String message) {
        this(stage, message, null, null, null, null);
    }

    public KdbxReadException(Stage stage, String message, Throwable cause) {
        this(stage, message, cause, null, null, null);
    }

    public KdbxReadException(String message) {
        this(Stage.UNKNOWN, message, null, null, null, null);
    }

    public KdbxReadException(Stage stage, String message, Throwable cause,
            Integer majorVersion, String cipherId, String kdfUuid) {
        super(message, cause);
        this.stage = stage;
        this.majorVersion = majorVersion;
        this.cipherId = cipherId;
        this.kdfUuid = kdfUuid;
    }

    public Stage stage() {
        return stage;
    }

    public Integer majorVersion() {
        return majorVersion;
    }

    /** 非敏感：cipherId 的 hex（KeePass 公开 UUID），可能为 null。 */
    public String cipherId() {
        return cipherId;
    }

    /** 非敏感：KDF uuid 的 hex（KeePass 公开 UUID），可能为 null。 */
    public String kdfUuid() {
        return kdfUuid;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(stage.label).append("] ").append(super.getMessage());
        if (majorVersion != null) {
            sb.append("; 版本主号=").append(majorVersion);
        }
        if (cipherId != null) {
            sb.append("; cipher=").append(cipherId);
        }
        if (kdfUuid != null) {
            sb.append("; kdf=").append(kdfUuid);
        }
        return sb.toString();
    }
}
