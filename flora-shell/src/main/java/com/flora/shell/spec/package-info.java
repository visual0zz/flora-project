/**
 * 参数声明与解析。
 * <p>命令通过 {@link com.flora.shell.spec.ArgSpec} 声明式描述参数（选项、位置参数、
 * 类型约束、默认值、组合规则），{@link com.flora.shell.spec.ArgParser} 从这份声明解析
 * argv 序列或结构化 Map。声明是解析、help 生成、Agent schema 的共同来源。</p>
 */
package com.flora.shell.spec;
