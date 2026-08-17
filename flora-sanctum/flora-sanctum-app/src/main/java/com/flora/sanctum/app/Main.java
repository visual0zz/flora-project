package com.flora.sanctum.app;

import com.flora.root.runtime.log.Level;
import com.flora.root.runtime.log.LogConfig;
import com.flora.sanctum.app.command.AddCommand;
import com.flora.sanctum.app.command.ChangePasswordCommand;
import com.flora.sanctum.app.command.CreateCommand;
import com.flora.sanctum.app.command.ExportCommand;
import com.flora.sanctum.app.command.GetCommand;
import com.flora.sanctum.app.command.ListCommand;
import com.flora.sanctum.app.command.SyncCommand;
import com.flora.sanctum.app.command.UnlockCommand;
import com.flora.shell.CommandResult;
import com.flora.shell.CommandService;
import com.flora.shell.InputEvent;
import com.flora.shell.UsageScenario;

import java.util.Arrays;
import java.util.List;
import java.nio.file.Path;

/**
 * flora-sanctum 应用入口（单一可执行 jar）。
 * <p>
 * - 无参数：启动 Swing GUI。
 * - 有参数：经 flora-shell 命令基座处理命令行。
 * 命令：create / unlock / add / list / get / sync
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            // 无参数 → 启动 GUI（先做形态识别：独立仓库形态直接解锁该仓库）
            // 仓库根经启动脚本 -Dflora.repo 传入（与配置/脚本同目录）；未传则回退到当前工作目录
            String repoProp = System.getProperty("flora.repo");
            Path repoRoot = repoProp != null && !repoProp.isBlank()
                    ? Path.of(repoProp).toAbsolutePath()
                    : Path.of("").toAbsolutePath();
            if (com.flora.sanctum.app.bootstrap.VaultForm.detect(repoRoot) == com.flora.sanctum.app.bootstrap.VaultForm.Type.STANDALONE) {
                Path root = com.flora.sanctum.app.bootstrap.VaultForm.vaultRoot(repoRoot);
                com.flora.sanctum.app.ui.SanctumGui.launchDirect(repoRoot, root, args);
            } else {
                // 应用形态 → 选择界面（新建/导入/打开）
                new com.flora.sanctum.app.ui.SelectScreen(vaultRoot ->
                        com.flora.sanctum.app.ui.SanctumGui.launchOpen(vaultRoot, args)).show();
            }
            return;
        }
        // 配置命令日志输出到控制台
        LogConfig.configure(cfg -> cfg
                .rootLevel(Level.INFO)
                .console(cc -> cc.pattern("%msg%n")));
        // 有参数 → 经 flora-shell 命令基座分派
        CommandService service = new CommandService(UsageScenario.CLI);
        service.register(new CreateCommand());
        service.register(new UnlockCommand());
        service.register(new AddCommand());
        service.register(new ListCommand());
        service.register(new GetCommand());
        service.register(new SyncCommand());
        service.register(new ChangePasswordCommand());
        service.register(new ExportCommand());

        CommandResult result = service.submit(InputEvent.ofCliArgs(Arrays.asList(args)));
        if (result.status() == CommandResult.Status.SYSTEM_ERROR) {
            System.err.println("命令执行失败");
            System.exit(1);
        }
    }
}
