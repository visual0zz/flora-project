package com.flora.sanctum.app;

import com.flora.sanctum.app.bootstrap.LogSetup;
import com.flora.sanctum.app.bootstrap.VaultDetector;
import com.flora.sanctum.app.ui.SanctumGui;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

import java.nio.file.Path;

/**
 * flora-sanctum 应用入口（单一可执行 jar）。GUI 是唯一交互方式。
 * <p>
 * 启动时在 jar 内做形态判定（见设计"形态与启动"）：
 * - 孤立仓库形态（jar 位于 lib/ 且仓库根存在 edit 脚本）→ 直接进入该仓库的解锁页；
 * - 应用形态 → 进入历史仓库列表页（新建/导入/打开合并于此）。
 */
public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        LogSetup.install();
        Path standaloneRoot = VaultDetector.detectStandaloneRoot();
        if (standaloneRoot != null) {
            LOG.info("Starting in standalone repository mode, root={}", standaloneRoot);
            Path vaultRoot = VaultDetector.vaultRoot(standaloneRoot);
            SanctumGui.launchDirect(standaloneRoot, vaultRoot);
        } else {
            LOG.info("Starting in application mode");
            SanctumGui.launch();
        }
    }
}
