package com.flora.mock.jsonschema;

/**
 * 生成器配置。
 *
 * @param maxDepth     递归 schema 的最大展开深度（默认 3），防 {@code $ref} 无限递归
 * @param targetLength 推荐长度（默认 64）：生成实例的规模感目标，
 *                     内部字符串/数组/对象/额外属性按经验比例朝该值靠拢，不做最小最大硬限制
 */
public record GenerationConfig(
        int maxDepth,
        int targetLength) {

    public static GenerationConfig defaultConfig() {
        return new GenerationConfig(3, 64);
    }
}
