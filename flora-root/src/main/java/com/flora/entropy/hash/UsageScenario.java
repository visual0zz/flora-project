package com.flora.entropy.hash;

/**
 * 哈希使用场景枚举。
 * <ul>
 *   <li>{@link #ENCRYPTING} — 加密安全场景</li>
 *   <li>{@link #ADDRESSING} — 地址映射/寻址场景</li>
 *   <li>{@link #FAST} — 快速非加密场景</li>
 *   <li>{@link #COMPATIBLE} — 兼容性场景</li>
 * </ul>
 */
public enum UsageScenario {
    /**
     * 加密安全场景
     */
    ENCRYPTING,
    /**
     * 地址映射/寻址场景
     */
    ADDRESSING,
    /**
     * 快速非加密场景
     */
    FAST,
    /**
     * 兼容性场景
     */
    COMPATIBLE,
}
