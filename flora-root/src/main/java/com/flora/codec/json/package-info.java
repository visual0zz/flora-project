/**
 * JSON 核心数据模型与处理。
 *
 * <p><b>公开 API（外部应只依赖这些类型）</b>：
 * <ul>
 *   <li>值模型根接口 {@link com.flora.codec.json.JsonValue}；</li>
 *   <li>容器值 {@link com.flora.codec.json.JsonObject}、{@link com.flora.codec.json.JsonArray}；</li>
 *   <li>标量值 {@link com.flora.codec.json.JsonString}、{@link com.flora.codec.json.JsonNumber}、
 *       {@link com.flora.codec.json.JsonBool}、{@link com.flora.codec.json.JsonNull}；</li>
 *   <li>门面/引擎 {@link com.flora.codec.json.JsonParser}、{@link com.flora.codec.json.JsonBuilder}、
 *       {@link com.flora.codec.json.JsonPath} 与聚合门面 {@code com.flora.codec.JsonUtil}。</li>
 * </ul>
 *
 * <p><b>包内实现细节（不应被外部直接依赖）</b>：
 * {@code com.flora.codec.json.impl} 子包承载内部实现：{@code JsonConversions} 是
 * 原生值↔JsonValue 桥接类、{@code JsonPathTokenizer/JsonPathParser/JsonPathEvaluator}
 * 是 JSONPath 引擎的词法/语法/求值内部类。调用方应经
 * {@link com.flora.codec.json.JsonObject#put(String, Object)} /
 * {@link com.flora.codec.json.JsonArray#add(Object)} 的自动包裹能力，而非直接使用它们。</p>
 */
package com.flora.codec.json;
