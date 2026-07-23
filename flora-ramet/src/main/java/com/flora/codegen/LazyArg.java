package com.flora.codegen;

/**
 * 惰性求值的函数参数包装。
 * 函数在调用时不立即求值，而是通过 {@link #eval()} 在需要时触发求值。
 * 支持短路场景（如 and/or）跳过不必要的求值。
 */
@FunctionalInterface
public interface LazyArg {
    Object eval();
}
