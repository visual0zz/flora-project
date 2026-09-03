package com.flora.sanctum.core.io.importer;

import com.flora.root.runtime.log.Logger;

/** {@link ImportListener} 的常用实现。 */
public final class ImportListeners {

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

    /** 把进度与告警转发到标准输出（用于测试/无头环境直接观察，不依赖日志框架）。 */
    public static ImportListener console() {
        return new ImportListener() {
            @Override
            public void onProgress(int done, int total, String stage) {
                System.out.println("[import] " + done + "/" + total + " " + stage);
            }

            @Override
            public void onWarning(String message) {
                System.out.println("[import:warn] " + message);
            }

            @Override
            public void onInfo(String message) {
                System.out.println("[import:info] " + message);
            }
        };
    }

    /**
     * 把进度与告警转发到外部传入的日志器。
     * <p>日志的落点（文件位置、滚动、XDG 等）完全由调用方决定，core 不感知、不负责创建。</p>
     *
     * @param logger 调用方提供的日志器，不得为 null
     */
    public static ImportListener logging(Logger logger) {
        return new ImportListener() {
            @Override
            public void onProgress(int done, int total, String stage) {
                logger.info("[import] {}/{} {}", done, total, stage);
            }

            @Override
            public void onWarning(String message) {
                logger.warn("[import:warn] {}", message);
            }

            @Override
            public void onInfo(String message) {
                logger.info("[import:info] {}", message);
            }
        };
    }
}
