package com.flora.ai.tool;

import com.flora.ai.chat.ToolCall;
import java.util.*;

/**
 * ToolCall 与 OpenAI/Anthropic 格式之间的转换工具。
 * <p>纯算法，不涉及网络。</p>
 */
public class ToolCallFormatter {

    private ToolCallFormatter() {}

    /** 从 LLM 返回的 tool_calls 参数列表中提取 ToolCall。 */
    public static List<ToolCall> fromRawList(List<Map<String, Object>> rawCalls) {
        if (rawCalls == null || rawCalls.isEmpty()) return List.of();
        List<ToolCall> result = new ArrayList<>();
        for (Map<String, Object> raw : rawCalls) {
            String id = (String) raw.get("id");
            String type = (String) raw.get("type");
            @SuppressWarnings("unchecked")
            Map<String, Object> func = (Map<String, Object>) raw.get("function");
            String name = func != null ? (String) func.get("name") : null;
            String args = func != null ? String.valueOf(func.get("arguments")) : null;
            result.add(new ToolCall(id != null ? id : "", name != null ? name : "", args != null ? args : "{}"));
        }
        return result;
    }

    /** 构造 OpenAI 格式的 tool_calls 列表，用于组装 ChatMessage。 */
    public static List<Map<String, Object>> toOpenAiFormat(List<ToolCall> calls) {
        if (calls == null) return List.of();
        return calls.stream().map(tc -> {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", tc.name());
            func.put("arguments", tc.arguments());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("id", tc.id());
            raw.put("type", "function");
            raw.put("function", func);
            return raw;
        }).toList();
    }
}
