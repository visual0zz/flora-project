package com.flora.codegen;

import java.util.List;

/**
 * 模板函数策略接口——Ramet 模板引擎中所有内置函数和 SPI 扩展函数必须实现的接口。
 *
 * <p>每个实现类需要提供以下信息：
 * <ul>
 *   <li>{@link #name()}：函数名，用于在模板表达式中匹配调用（大小写敏感）</li>
 *   <li>{@link #arity()}：函数期望的参数个数，-1 表示可变参数</li>
 *   <li>{@link #apply(List)}：执行函数逻辑，返回计算结果</li>
 * </ul>
 *
 * <p>内置函数通过 {@link com.flora.codegen.engine.runtime.FunctionRegistry} 注册。
 * 外部模块可通过 Java SPI 机制扩展：在 {@code META-INF/services/com.flora.codegen.TemplateFunction}
 * 中声明实现类的全限定名。
 *
 * @see com.flora.codegen.engine.runtime.FunctionRegistry
 */
public interface TemplateFunction {

    /** 函数名，用于在模板中匹配调用（大小写敏感）。 */
    String name();

    /** 函数期望的参数个数。 */
    int arity();

    /** 对惰性参数列表执行转换，返回结果。 */
    Object apply(List<LazyArg> args);
}
