package com.flora.ramet.engine;

/**
 * 模板原始来源：一个稳定的标识 {@code key} 加上未经解析的文本 {@code text}。
 *
 * <p>入口模板与 {@code <#include>} 引用的子模板在此层面完全同构——
 * 二者都是「一段文本 + 一个用于在仓库中定位的 key」。解析产物见 {@link Template}。
 */
public record TemplateSource(String key, String text) {

    /** 构造一个无 key 的源（多用于一次性内存解析，如内联 @Path 表达式）。 */
    public static TemplateSource of(String text) {
        return new TemplateSource("", text);
    }

    /** 构造带 key 的源。 */
    public static TemplateSource of(String key, String text) {
        return new TemplateSource(key, text);
    }
}
