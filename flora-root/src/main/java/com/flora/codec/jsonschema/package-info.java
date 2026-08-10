/**
 * JSON Schema 2020-12 校验。
 *
 * <p><b>公开 API（外部应只依赖这些类型）</b>：
 * <ul>
 *   <li>校验门面 {@link com.flora.codec.jsonschema.JsonSchema}；</li>
 *   <li>结果类型 {@link com.flora.codec.jsonschema.ValidationResult}、
 *       {@link com.flora.codec.jsonschema.ValidationError}；</li>
 *   <li>值类型工具 {@link com.flora.codec.jsonschema.JsonTypes}。</li>
 * </ul>
 *
 * <p><b>包内实现细节（不应被外部直接依赖）</b>：
 * {@code com.flora.codec.jsonschema.impl} 子包承载编译与求值引擎（
 * {@code SchemaRegistry}/{@code CompiledSchema}/{@code ValidationContext} 等），
 * {@code validator}/{@code format} 子包分别为关键字校验器与格式校验器注册表。</p>
 */
package com.flora.codec.jsonschema;
