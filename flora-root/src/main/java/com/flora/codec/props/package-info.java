/**
 * Properties 格式解析与序列化实现包。
 * <p>将 {@code .properties} 文本解析/构建为嵌套 {@code Map}，
 * 点号键自动展开（如 {@code a.b.c=1} → {@code {a:{b:{c:"1"}}}）。</p>
 */
package com.flora.codec.props;
