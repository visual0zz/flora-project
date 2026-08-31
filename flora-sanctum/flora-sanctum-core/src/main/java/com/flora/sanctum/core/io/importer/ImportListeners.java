package com.flora.sanctum.core.io.importer;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

/** {@link ImportListener} 的常用实现。 */
public final class ImportListeners {

    private static final Logger LOG = LoggerFactory.getLogger(ImportListeners.class);

    private ImportListeners() {
    }

    /** 丢弃所有回调的无操作实现。 */
    public static ImportListener noop() {
        return new ImportListener() {
            @Override
            public void onProgress(int done, int total, String stage) {
            }

            @Override
            public void onWarning(String message) {
            }
        };
    }

    /** 把进度与告警转发到控制台（用于测试/无头环境）。 */
    public static ImportListener console() {
        return new ImportListener() {
            @Override
            public void onProgress(int done, int total, String stage) {
                LOG.info("[import] {}/{} {}", done, total, stage);
            }

            @Override
            public void onWarning(String message) {
                LOG.warn("[import:warn] {}", message);
            }
        };
    }
}
