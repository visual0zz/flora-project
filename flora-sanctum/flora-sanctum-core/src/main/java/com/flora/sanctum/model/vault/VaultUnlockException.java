package com.flora.sanctum.model.vault;

/**
 * 解锁失败：报告失败发生的**阶段**，而非统一笼统的"解锁失败"。
 * <p>
 * 阶段区分让上层（GUI）能给出针对性提示；注意部分失败（如 {@link Phase#MANIFEST_CORRUPT}）
 * 在密码学上无法与文件篡改区分，消息如实同时列出可能原因，不做虚假的单一归因。
 */
public final class VaultUnlockException extends RuntimeException {

    /** 解锁失败阶段（按解锁管线推进顺序）。 */
    public enum Phase {
        /** 找不到 manifest 引导块：目录不是 Sanctum 仓库，或文件魔数/结构损坏。 */
        NOT_A_VAULT("文件不是 Sanctum 仓库（缺少 manifest 引导块，魔数/结构校验失败）"),
        /** manifest 可定位但内容解析或 MAC 校验失败：主密码错误，或 manifest 已被篡改/损坏。 */
        MANIFEST_CORRUPT("主密码错误，或 manifest 已被篡改/损坏"),
        /** manifest 记录了根对象 uuid，但仓库中找不到对应块。 */
        ROOT_MISSING("仓库损坏：缺少必要节点（根对象缺失）"),
        /** 根对象块存在，但用 KEK 无法解密（可能已被篡改）。 */
        ROOT_DECRYPT_FAILED("仓库损坏：根对象无法解密（可能已被篡改）"),
        /** 根对象可解密，但缺少必要字段（dek / repoKeyIdSeed）。 */
        ROOT_INCOMPLETE("仓库损坏：根对象内容不完整（缺少必要字段）");

        private final String message;

        Phase(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Phase phase;

    public VaultUnlockException(Phase phase) {
        super(phase.message());
        this.phase = phase;
    }

    public Phase phase() {
        return phase;
    }
}
