package com.flora.codegen.engine;

/**
 * Ramet 模板引擎异常——编译期与运行时错误统一通过此异常抛出。
 *
 * <p>异常可能来源于以下阶段：
 * <ul>
 *   <li>词法分析/语法分析：模板语法错误</li>
 *   <li>元数据解析：{@code @Param}、{@code @Combine}、{@code @Path} 声明错误</li>
 *   <li>引用解析：变量引用目标不存在或类型不匹配</li>
 *   <li>模板渲染：运行时表达式求值失败</li>
 * </ul>
 *
 * <p>继承 {@link RuntimeException}，为非受检异常。
 */
public final class CodeGenException extends RuntimeException {
    public CodeGenException(String msg) {
        super(msg);
    }

    public CodeGenException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
