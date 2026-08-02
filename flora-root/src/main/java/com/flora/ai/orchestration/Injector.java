package com.flora.ai.orchestration;

import java.util.concurrent.CompletableFuture;

/**
 * 投影注入器：向 {@link ProjectionBuilder} 追加本次投影要展示的内容。
 * <p>统一了静态提示、世界观、记忆、RAG 检索结果等所有"注入"形态：
 * 静态内容直接写入；慢检索（如 RAG）返回异步结果，由投影器并行等待。
 * 注入不修改 {@link Context} 主状态，属于投影期的只读增强。</p>
 *
 * <pre>{@code
 * // 静态注入：系统提示
 * Injector staticSystem = b -> {
 *     b.system("你是...");
 *     return CompletableFuture.completedFuture(null);
 * };
 *
 * // 异步注入：RAG 检索结果（thenAccept 内为语句，忽略 inject 返回值）
 * Injector rag = b -> http.searchAsync(query).thenAccept(hits -> {
 *     b.inject(Message.of(Message.Role.USER, hits.toPrompt()));
 * });
 * }</pre>
 */
@FunctionalInterface
public interface Injector {

    /** 向投影构建器追加内容；慢操作可返回未完成的 future。 */
    CompletableFuture<Void> inject(ProjectionBuilder builder);

    /** 便捷：静态内容注入（同步完成）。 */
    static Injector of(Runnable action) {
        return b -> {
            action.run();
            return CompletableFuture.completedFuture(null);
        };
    }
}
