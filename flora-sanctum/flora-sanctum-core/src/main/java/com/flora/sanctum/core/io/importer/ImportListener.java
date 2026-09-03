package com.flora.sanctum.core.io.importer;

/**
 * 导入过程回调：进度、告警与诊断信息。实现可为 UI 进度条或日志；无操作实现见 {@link ImportListeners#noop()}.
 */
public interface ImportListener {

    /** 进度更新。done/total 为条目级计数，stage 为当前阶段描述。 */
    void onProgress(int done, int total, String stage);

    /** 非致命告警（如某字段无法映射）。 */
    void onWarning(String message);

    /**
     * 诊断性信息（非告警，如导入统计摘要），便于从日志回溯具体问题。
     * <p>默认空实现，旧实现无需改动即可兼容。</p>
     *
     * @param message 诊断信息
     */
    default void onInfo(String message) {
    }
}
