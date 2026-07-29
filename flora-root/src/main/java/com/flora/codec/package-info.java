/**
 * 序列化与解析工具包（编码/解码）。
 * <p>统一门面入口：{@link com.flora.codec.JsonUtil}、{@link com.flora.codec.YamlUtil}、
 * {@link com.flora.codec.TomlUtil}、{@link com.flora.codec.PropsUtil}。
 * 每种格式均包含 Parser 与 Builder 实现（位于各自子包），输出统一的内存模型
 * （{@code Map<String, Object>} 嵌套结构）。</p>
 *
 * <p>格式子包：
 * <ul>
 *   <li>{@code json} — JSON 解析/序列化 + JSONPath 查询</li>
 *   <li>{@code yaml} — YAML 解析/序列化</li>
 *   <li>{@code toml} — TOML 解析/序列化</li>
 *   <li>{@code props} — Java Properties 解析/序列化</li>
 * </ul>
 */
package com.flora.codec;
