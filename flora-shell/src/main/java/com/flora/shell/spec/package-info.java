/**
 * 参数声明与解析。
 * <p>命令通过 {@link com.flora.shell.spec.ArgSpec} 声明式描述参数（选项、位置参数、
 * 类型约束、默认值、组合规则），{@link com.flora.shell.spec.ArgParser} 从这份声明把
 * argv 序列或结构化 JSON 统一解析/校验为 {@code JsonObject}（命令输入的统一形态）。
 * 声明是解析与 help 生成的共同来源。</p>
 */
package com.flora.shell.spec;
