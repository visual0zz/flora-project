package com.flora.ai.orchestration;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.InferenceConfig;
import com.flora.ai.api.Message;
import com.flora.ai.api.ToolSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 对话投影器：把 {@link Context} 折叠成模型可见的 {@link ChatRequest}。
 * <p>每次投影都从零开始：注入器链（系统提示/记忆/RAG/世界观）并行执行写入
 * {@link ProjectionBuilder}，全部完成后与历史上下文合并，再经 {@link TokenBudget}
 * 裁剪，最终产出请求。投影为纯操作，不修改 {@link Context}。</p>
 * <p>支持同步（{@link #assemble}）与异步（{@link #assembleAsync}）两种形态：
 * 异步形态并行等待所有注入器，慢检索（RAG）不阻塞调用线程。</p>
 */
public final class ChatProjector {

    private final List<Injector> injectors;
    private final TokenBudget budget;
    private final ToolRegistry tools;
    private final InferenceConfig config;

    private ChatProjector(List<Injector> injectors, TokenBudget budget,
                          ToolRegistry tools, InferenceConfig config) {
        this.injectors = List.copyOf(injectors);
        this.budget = budget;
        this.tools = tools;
        this.config = config;
    }

    /** 构造投影器（注入器链 + 预算 + 工具注册表 + 推理配置）。 */
    public static ChatProjector of(List<Injector> injectors, TokenBudget budget,
                                   ToolRegistry tools, InferenceConfig config) {
        return new ChatProjector(injectors, budget, tools, config);
    }

    /** 当前 token 预算（供自动压缩等决策使用）。 */
    public TokenBudget budget() {
        return budget;
    }

    /** 同步投影。 */
    public ChatRequest assemble(Context context) {
        try {
            return assembleAsync(context).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    /** 异步投影：并行等待全部注入器完成后合并。 */
    public CompletableFuture<ChatRequest> assembleAsync(Context context) {
        ProjectionBuilder builder = new ProjectionBuilder();
        CompletableFuture<?>[] futures = injectors.stream()
                .map(in -> in.inject(builder))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures)
                .thenApply(v -> build(context, builder));
    }

    private ChatRequest build(Context context, ProjectionBuilder builder) {
        List<Message> history = budget.trim(context.messages());
        List<ToolSpec> toolsList = tools == null ? List.of() : tools.declarations();
        return builder.build(history, toolsList, config);
    }
}
