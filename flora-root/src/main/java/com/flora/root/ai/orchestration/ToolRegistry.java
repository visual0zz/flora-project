package com.flora.root.ai.orchestration;

import com.flora.root.ai.api.Message;
import com.flora.root.ai.api.ToolCall;
import com.flora.root.ai.api.ToolSpec;
import com.flora.root.tag.ThreadFragile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：声明与执行的统一入口。
 * <p>注册 {@link ToolSpec}（声明，进请求）与对应执行器（{@link Executor}，执行调用）。
 * 执行结果统一包装为 TOOL 角色回执消息（{@code toolResult}），可直接落回上下文。
 * 执行器抛异常时自动转错误回执（{@code isError=true}）。</p>
 */
@ThreadFragile("内部可变注册表，非线程安全；同一实例建议单线程使用")
public final class ToolRegistry {

    /** 工具执行器：输入模型发起的调用，返回回执消息。 */
    @FunctionalInterface
    public interface Executor {
        Message execute(ToolCall call);
    }

    private final Map<String, ToolSpec> specs = new LinkedHashMap<>();
    private final Map<String, Executor> executors = new LinkedHashMap<>();

    /** 注册工具：声明 + 执行器。 */
    public ToolRegistry register(ToolSpec spec, Executor executor) {
        specs.put(spec.name(), spec);
        executors.put(spec.name(), executor);
        return this;
    }

    /** 全部工具声明（进 {@code ChatRequest.tools()}）。 */
    public List<ToolSpec> declarations() {
        return List.copyOf(specs.values());
    }

    /** 执行单个调用，返回回执消息；未注册或执行异常 → 错误回执。 */
    public Message execute(ToolCall call) {
        Executor ex = executors.get(call.name());
        if (ex == null) {
            return Message.toolResult(call.id(), "未注册的工具: " + call.name(), true);
        }
        try {
            return ex.execute(call);
        } catch (RuntimeException e) {
            return Message.toolResult(call.id(), "工具执行失败: " + e.getMessage(), true);
        }
    }

    /** 执行一批调用（并行工具调用），按顺序返回回执消息。 */
    public List<Message> executeAll(List<ToolCall> calls) {
        return calls.stream().map(this::execute).toList();
    }

    /** 是否包含某工具。 */
    public boolean contains(String name) {
        return executors.containsKey(name);
    }
}
