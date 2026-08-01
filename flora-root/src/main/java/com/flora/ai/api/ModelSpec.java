package com.flora.ai.api;

/**
 * 模型规格：模型标识、厂商、规模级别。
 */
public record ModelSpec(String id, String provider, Size size) {

    public enum Size {
        /** 大模型（旗舰，如 GPT-5.2、Opus、Gemini Pro）。 */
        LARGE,
        /** 中端模型。 */
        MID,
        /** 轻量/快速模型（flash、mini、haiku）。 */
        FLASH
    }

    /** 便捷构造：规模由调用方显式指定。 */
    public static ModelSpec of(String id, String provider) {
        return new ModelSpec(id, provider, null);
    }

    public static ModelSpec of(String id, String provider, Size size) {
        return new ModelSpec(id, provider, size);
    }
}
