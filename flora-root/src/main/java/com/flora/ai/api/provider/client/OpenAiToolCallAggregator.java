package com.flora.ai.api.provider.client;

import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.ToolCall;
import com.flora.codec.json.JsonParser;
import com.flora.tag.ThreadFragile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 流式 {@code tool_calls} 碎片聚合器。
 * <p>OpenAI 流式的工具调用按 {@code index} 分片传输，且无 per-index 完成标记
 * （id/name 仅首片出现，arguments 逐片到达）。本类按 index 累积碎片，在协议层信号
 * （{@code finish_reason="tool_calls"} 或流结束）到来时整批 flush 出完整调用。
 * arguments 分片拼接后解析为 Map；解析失败时保留空 Map，原始串由事件 {@code rawArguments} 带出。</p>
 */
@ThreadFragile("内部按 index 累积碎片的可变 Map 状态，非线程安全；单次流式调用内单线程使用")
final class OpenAiToolCallAggregator {

    private final Map<Integer, StringBuilder> argsByIndex = new LinkedHashMap<>();
    private final Map<Integer, String> idsByIndex = new LinkedHashMap<>();
    private final Map<Integer, String> namesByIndex = new LinkedHashMap<>();

    /** 接收一片碎片；id/name 仅首片非 null。 */
    void add(int index, String id, String name, String argsFragment) {
        if (id != null) {
            idsByIndex.put(index, id);
        }
        if (name != null) {
            namesByIndex.put(index, name);
        }
        // 只要该 index 出现过（id/name/参数任一）即登记，空参数调用也不应丢失
        if (id != null || name != null || (argsFragment != null && !argsFragment.isEmpty())) {
            argsByIndex.computeIfAbsent(index, k -> new StringBuilder());
        }
        if (argsFragment != null && !argsFragment.isEmpty()) {
            argsByIndex.get(index).append(argsFragment);
        }
    }

    boolean isEmpty() {
        return argsByIndex.isEmpty();
    }

    /** 整批 flush 已攒齐的调用，返回完整事件列表并清空内部状态。 */
    List<StreamEvent.ToolCallCompleted> flush() {
        List<StreamEvent.ToolCallCompleted> out = new ArrayList<>();
        for (var e : argsByIndex.entrySet()) {
            int index = e.getKey();
            String raw = e.getValue().toString();
            out.add(new StreamEvent.ToolCallCompleted(
                    new ToolCall(idsByIndex.get(index), namesByIndex.get(index), parseArgs(raw)), raw));
        }
        argsByIndex.clear();
        idsByIndex.clear();
        namesByIndex.clear();
        return out;
    }

    private static Map<String, Object> parseArgs(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Object v = JsonParser.parse(raw).toMap();
            return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        } catch (IllegalStateException ignored) {
            return Map.of();
        }
    }
}
