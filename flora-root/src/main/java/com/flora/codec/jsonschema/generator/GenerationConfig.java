package com.flora.codec.jsonschema.generator;

/**
 * 生成器配置。
 *
 * @param maxDepth       递归 schema 的最大展开深度（默认 3）
 * @param minStringLen   随机字符串最小长度（默认 1）
 * @param maxStringLen   随机字符串最大长度（默认 16）
 * @param minArrayLen    数组默认最小长度（默认 0）
 * @param maxArrayLen    数组默认最大长度（默认 5）
 * @param extraProps     对象默认额外属性数上限（默认 2，受 additionalProperties 约束）
 */
public record GenerationConfig(
        int maxDepth,
        int minStringLen,
        int maxStringLen,
        int minArrayLen,
        int maxArrayLen,
        int extraProps) {

    public static GenerationConfig defaultConfig() {
        return new GenerationConfig(3, 1, 16, 0, 5, 2);
    }
}
