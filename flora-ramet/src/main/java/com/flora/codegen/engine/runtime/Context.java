package com.flora.codegen.engine.runtime;

import com.flora.codegen.CompiledTemplate;
import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.ast.MacroDefNode;

import java.util.*;

/**
 * Ramet 模板引擎的渲染上下文——持有模板渲染所需的全部运行时状态。
 *
 * <p>职责：
 * <ul>
 *   <li>变量作用域管理：通过链式 {@link #child()} 支持嵌套作用域，
 *       变量查找沿父链向上回溯（先查 params 再查局部变量）</li>
 *   <li>参数存储：{@code params} 存放宿主提供的参数（如笛卡尔积展开后的变量），优先级最高</li>
 *   <li>宏注册：{@code macros} 存放当前模板中定义的宏定义，可供子作用域共享</li>
 *   <li>包含管理：{@code includes} 存放已编译的子模板，供 include 指令引用</li>
 *   <li>函数注册表：{@link FunctionRegistry}，提供内置函数和 SPI 扩展的函数调用能力</li>
 *   <li>循环引用检测：{@code includeChain} 追踪当前 include 调用链，防止无限递归</li>
 * </ul>
 *
 * <p>使用 {@link #of(Map, Map)} 工厂方法创建实例。
 */
public final class Context {
    private final Map<String, Object> vars = new HashMap<>();
    public final Map<String, Object> params;
    private Map<String, MacroDefNode> macros;
    public final Map<String, CompiledTemplate> includes;
    /**
     * 当前正在渲染的模板相对路径（相对模板根目录，正斜杠）。
     * 用于 include 路径以「发起 include 的文件所在文件夹」为基准做相对解析。
     * 顶层模板由 {@code CodeGenUtil.generate} 传入；被 include 的模板由
     * {@link IncludeNode} 在解析出目标 key 后更新为对应的相对路径。可能为 null
     * （如纯内存接口触发的渲染），此时回退为相对模板根目录解析。
     */
    public final String source;
    /** 函数注册表（策略模式），用于解析函数调用。 */
    public final FunctionRegistry functions;
    /**
     * 模板根目录（绝对路径）。用于 include 以 {@code '/'} 开头的路径时，
     * 将其作为操作系统绝对路径并 relativize 为此目录下的相对 key 查找。
     */
    public final String templatesRoot;
    public Context parent;
    /**
     * 当前 include 调用链，用于检测循环依赖。
     * 由 IncludeNode 在 render 前写入，render 后清除。
     */
    private Set<String> includeChain;
    /**
     * 宏调用栈，用于错误消息中显示宏调用链。
     * 由 MacroCallNode 在 render 前压栈，render 后弹栈。
     */
    private Deque<String> macroCallChain;

    private Context(Map<String, Object> params, Map<String, CompiledTemplate> includes, String source, String templatesRoot) {
        this.params = params;
        this.includes = (includes != null) ? includes : Map.of();
        this.macros = new HashMap<>();
        this.functions = new FunctionRegistry();
        this.parent = null;
        this.includeChain = new LinkedHashSet<>();
        this.macroCallChain = new LinkedList<>();
        this.source = source;
        this.templatesRoot = templatesRoot;
    }

    /** 构造一个渲染上下文（顶层模板，source 未知）。 */
    public static Context of(Map<String, Object> params,
                              Map<String, CompiledTemplate> includes) {
        return new Context(params, includes, null, null);
    }

    /** 构造一个渲染上下文，并指定当前模板的相对路径 source。 */
    public static Context of(Map<String, Object> params,
                              Map<String, CompiledTemplate> includes, String source) {
        return new Context(params, includes, source, null);
    }

    /** 构造一个渲染上下文，指定 source 和模板根目录。 */
    public static Context of(Map<String, Object> params,
                              Map<String, CompiledTemplate> includes, String source, String templatesRoot) {
        return new Context(params, includes, source, templatesRoot);
    }

    public Context child() {
        return child(this.source);
    }

    /** 创建子作用域，并把当前模板相对路径更新为 {@code source}（include 解析时使用）。 */
    public Context child(String source) {
        Context c = new Context(params, includes, source, this.templatesRoot);
        c.parent = this;
        c.macros = this.macros;
        c.includeChain = this.includeChain; // 共享引用，保证跨层检测
        c.macroCallChain = this.macroCallChain; // 共享引用，宏调用跨上下文
        return c;
    }

    // ---- 宏管理 ----

    /** 按名称查找已注册的宏定义，沿父链回溯。 */
    public MacroDefNode getMacro(String name) {
        return macros.get(name);
    }

    /** 注册宏定义到当前上下文。 */
    public void putMacro(String name, MacroDefNode m) {
        macros.put(name, m);
    }

    // ---- include 循环检测 ----

    /** 尝试将 path 加入 include 调用链。返回 true 表示添加成功（无循环），false 表示已存在（循环依赖）。 */
    public boolean addIncludeChain(String path) {
        return includeChain.add(path);
    }

    /** 从 include 调用链中移除 path。 */
    public void removeIncludeChain(String path) {
        includeChain.remove(path);
    }

    /** 返回 include 调用链字符串，如 "a.ramet → b.ramet"。空链返回 null。 */
    public String getIncludePath() {
        if (includeChain.isEmpty()) return null;
        return String.join(" → ", includeChain);
    }

    // ---- 宏调用栈 ----

    /** 将宏调用条目压栈。 */
    public void pushMacroCall(String entry) {
        macroCallChain.addLast(entry);
    }

    /** 将最近入栈的宏调用条目弹栈。 */
    public void popMacroCall() {
        macroCallChain.removeLast();
    }

    /**
     * 返回宏调用链字符串，如 "myMacro @ Foo.java.ftl:5 → innerMacro @ Bar.java.ftl:12"。
     * 空链返回 null。
     */
    public String getMacroCallChain() {
        if (macroCallChain.isEmpty()) return null;
        return String.join(" → ", macroCallChain);
    }

    // ---- 变量管理 ----

    public Object getVar(String name) {
        Context c = this;
        while (c != null) {
            if (c.vars.containsKey(name)) return c.vars.get(name);
            c = c.parent;
        }
        return null;
    }

    public void setVar(String name, Object value) {
        this.vars.put(name, value);
    }

    public Object lookup(String name) {
        String[] parts = name.split("\\.");
        Object cur = lookupFirst(parts[0]);
        for (int k = 1; k < parts.length && cur != null; k++) {
            cur = TemplateUtils.getProperty(cur, parts[k]);
        }
        return cur;
    }

    public Object lookupFirst(String first) {
        // 优先从全局 params 查找（宿主笛卡尔积展开变量，最高优先级）
        Object v = params.get(first);
        if (v != null) return v;
        // 最后从局部变量查找
        return getVar(first);
    }
}
