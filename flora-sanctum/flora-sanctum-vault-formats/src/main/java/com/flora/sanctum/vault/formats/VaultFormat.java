package com.flora.sanctum.vault.formats;

/**
 * 第三方密码仓库格式枚举（本模块支持的只读导入来源）。
 */
public enum VaultFormat {
    /** Bitwarden 明文 JSON 导出（文件后缀通常为 .json）。 */
    BITWARDEN,
    /** KeePass 1.x（.kdb 旧格式，二进制）。 */
    KEEPASS1,
    /** 1Password OPVault（目录，以 ZIP 传入）。 */
    OPVAULT,
    /** 1Password 1PUX（.1pux，本质为 ZIP）。 */
    ONEPUX
}
