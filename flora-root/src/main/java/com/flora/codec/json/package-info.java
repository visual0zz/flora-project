/**
 * JSON 编解码门面。
 *
 * <p><b>本包</b>：解析器 {@link com.flora.codec.json.JsonParser} 与序列化器
 * {@link com.flora.codec.json.JsonBuilder}，外加聚合门面 {@code com.flora.codec.JsonUtil}。
 *
 * <p><b>值模型（{@code com.flora.codec.json.model}）</b>：值模型根接口
 * {@link com.flora.codec.json.model.JsonValue}、容器值
 * {@link com.flora.codec.json.model.JsonObject}/{@link com.flora.codec.json.model.JsonArray}、
 * 标量值 {@link com.flora.codec.json.model.JsonString}/{@link com.flora.codec.json.model.JsonNumber}/
 * {@link com.flora.codec.json.model.JsonBool}/{@link com.flora.codec.json.model.JsonNull} 与
 * 序列化排除注解 {@link com.flora.codec.json.model.JsonIgnore}。
 *
 * <p><b>JSONPath（{@code com.flora.codec.json.path}）</b>：表达式引擎
 * {@link com.flora.codec.json.path.JsonPath}。
 *
 * <p><b>内部实现（不应被外部直接依赖）</b>：{@code model.impl}/{@code path.impl} 子包
 * 分别承载值模型桥接（{@code JsonConversions}）与 JSONPath 词法/语法/求值内部类；
 * 调用方应经 {@link com.flora.codec.json.model.JsonObject#put(String, Object)} /
 * {@link com.flora.codec.json.model.JsonArray#add(Object)} 的自动包裹能力，而非直接使用它们。</p>
 */
package com.flora.codec.json;
