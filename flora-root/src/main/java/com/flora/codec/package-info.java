/**
 * 序列化与解析工具包（编码/解码）。
 * <p>统一门面入口：{@link com.flora.codec.JsonUtil}、{@link com.flora.codec.YamlUtil}、
 * {@link com.flora.codec.TomlUtil}、{@link com.flora.codec.PropsUtil}。</p>
 * <p>JSON 子包以 {@link com.flora.codec.json.JsonValue} 为内部核心数据模型
 * （{@link com.flora.codec.json.JsonObject} / {@link com.flora.codec.json.JsonArray}
 * 与标量类型），{@link com.flora.codec.json.JsonObject} 为默认解析类型，并可通过
 * {@code toMap()} / {@code toNative()} 转换为 {@code Map<String, Object>} 原生树。
 * YAML / TOML / Properties 等仍输出统一的 {@code Map<String, Object>} 嵌套结构。</p>
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
