package com.flora.hanako.tools;

import com.flora.root.ai.api.ToolSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：汇总所有可用工具，提供「声明列表」与「按名分发执行」。
 * <p>声明列表转换为 {@link ToolSpec} 注入 LLM；执行时按工具名查表调用。</p>
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
    }

    /** 注册一个工具。 */
    public ToolRegistry add(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    /** 所有工具的 LLM 声明。 */
    public List<ToolSpec> specs() {
        List<ToolSpec> specs = new ArrayList<>();
        for (Tool t : tools.values()) {
            specs.add(ToolSpec.of(t.name(), t.description(), t.parametersSchema()));
        }
        return specs;
    }

    /** 按名执行工具；未找到返回错误文本。 */
    public String execute(String name, Map<String, Object> args) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return "错误：未知工具 " + name;
        }
        try {
            return tool.execute(args == null ? Map.of() : args);
        } catch (RuntimeException e) {
            return "错误：工具 " + name + " 执行异常 - " + e.getMessage();
        }
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }
}
