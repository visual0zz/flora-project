/**
 * 自研正则自动机包。
 * <p>提供 NFA/DFA 自动机引擎：{@code RegexCompiler} 将正则编译为自动机，
 * {@code Automaton} 提供匹配、按目标长度采样、交并补代数与可满足性检测。
 * 不兼容语法（环视、反向引用、命名组）在编译期抛 {@code AutomatonException}，
 * 不做静默特殊处理。支持扩展语法（POSIX 类、字符类并集差集、十六进制转义）。</p>
 */
package com.flora.mock.automaton;
