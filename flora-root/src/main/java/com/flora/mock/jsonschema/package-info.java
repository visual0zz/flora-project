/**
 * 基于 JSON Schema 的数据生成器包。
 * <p>入口为 {@link com.flora.mock.jsonschema.JsonGenerator}，
 * 依据 2020-12 schema 生成随机且符合约束的 JSON 实例。
 * 生成器复用 {@code com.flora.codec.jsonschema} 的 schema 编译与引用解析能力；
 * 内部实现位于 {@code com.flora.mock.jsonschema.impl} 子包（不导出）。</p>
 */
package com.flora.mock.jsonschema;
