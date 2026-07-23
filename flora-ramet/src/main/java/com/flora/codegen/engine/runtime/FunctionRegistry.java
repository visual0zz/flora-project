package com.flora.codegen.engine.runtime;

import com.flora.codegen.TemplateFunction;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 函数注册表，管理所有可用函数的注册与查找。
 *
 * <p>{@link #BUILTINS} 是内置函数名→参数个数的映射，由 {@link BuiltinFunc} 枚举自动派生，
 * 始终与函数定义同步。{@link com.flora.codegen.engine.parser.Lson.Keyword} 的三值检查
 * 和 {@link com.flora.codegen.engine.parser.Lson} 的指令表达式解析器均基于该映射判断关键字/运算符。
 *
 * <p>内置函数由 {@link BuiltinFunc} 枚举统一管理（一个 class 文件替代 25+ 匿名内部类），
 * 外部模块可通过 Java SPI 机制扩展：
 * <ol>
 *   <li>实现 {@link TemplateFunction} 接口</li>
 *   <li>在 {@code META-INF/services/com.flora.codegen.TemplateFunction} 文件中声明实现类的全限定名</li>
 * </ol>
 *
 * <p>内置函数和 SPI 扩展仅在类初始化时注册一次（{@code static} 块），
 * 之后每次构造实例时只需从全局注册表拷贝，避免重复加载。
 */
public final class FunctionRegistry {

    /** 所有内置函数名 → arity（-1 表示可变参数）。这是关键字判定的唯一来源。 */
    public static final Map<String, Integer> BUILTINS;

    static {
        var map = new HashMap<String, Integer>(32);
        for (var f : BuiltinFunc.values()) {
            var func = f.asFunc();
            map.put(func.name(), func.arity());
        }
        BUILTINS = Collections.unmodifiableMap(map);
    }

    /** 全局注册表：内置函数 + SPI 扩展。类初始化时加载一次，JVM 保证线程安全。 */
    private static final Map<String, TemplateFunction> GLOBAL = loadGlobal();

    private static Map<String, TemplateFunction> loadGlobal() {
        var map = new HashMap<String, TemplateFunction>(32);
        for (var f : BuiltinFunc.values()) {
            var func = f.asFunc();
            map.put(func.name(), func);
        }
        for (var f : ServiceLoader.load(TemplateFunction.class)) {
            var old = map.put(f.name(), f);
            if (old != null) {
                throw new IllegalStateException("SPI 函数名与内置函数冲突: " + f.name());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private final Map<String, TemplateFunction> functions = new HashMap<>();

    /** 创建注册表实例，从全局注册表拷贝所有函数。 */
    public FunctionRegistry() {
        functions.putAll(GLOBAL);
    }

    /** 向当前实例注册函数。不影响全局注册表和其他实例。 */
    public void register(TemplateFunction fn) {
        functions.put(fn.name(), fn);
    }

    public TemplateFunction get(String name) {
        return functions.get(name);
    }
}
