package com.flora.sanctum.app.io.importer;

/**
 * 导入过程回调：进度与告警。实现可为 UI 进度条或日志；无操作实现见 {@link ImportListeners#noop()}.
 */
public interface ImportListener {

    /** 进度更新。done/total 为条目级计数，stage 为当前阶段描述。 */
    void onProgress(int done, int total, String stage);

    /** 非致命告警（如某字段无法映射）。 */
    void onWarning(String message);
}
