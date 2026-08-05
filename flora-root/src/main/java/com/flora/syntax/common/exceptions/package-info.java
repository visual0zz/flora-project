/**
 * 语法分析异常：两级体系。
 * <p>{@code SyntaxException} 为基类（带位置消息，静态工厂 {@code at(int, String)}）；
 * {@code ParseException} 继承基类并补充结构化定位字段（行/列/偏移/期望项），
 * 由 PEG 识别器在输入不匹配时抛出。文法定义错误（原 GrammarException）统一并入
 * {@code SyntaxException}。</p>
 */
package com.flora.syntax.common.exceptions;
