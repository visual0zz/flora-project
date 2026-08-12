package com.flora.hanako.tools;

import com.flora.root.tag.WorkInProgress;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 终端命令工具：在工作目录内执行 shell 命令（带超时）。
 * <p>复刻 openhanako 终端工具；进程树杀灭（基座能力 F2）暂未实现，当前仅单进程 + 超时。
 * 出于安全默认，外部网络访问类命令由调用方自行承担。</p>
 */
@WorkInProgress("进程树杀灭（killTree）待基座能力 F2 落地，当前仅单进程 + 超时")
public final class TerminalTool implements Tool {

    private final Path workDir;
    private final long timeoutSeconds;

    public TerminalTool(Path workDir, long timeoutSeconds) {
        this.workDir = workDir;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String name() {
        return "run_command";
    }

    @Override
    public String description() {
        return "在工作目录内执行一条 shell 命令并返回标准输出/错误。可用于编译、运行脚本、列目录等。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("command", ReadFileTool.strProp("要执行的 shell 命令字符串"));
        return ReadFileTool.objSchema(props, List.of("command"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String command = ReadFileTool.asString(args.get("command"));
        if (command == null || command.isBlank()) {
            return "错误：缺少 command 参数";
        }
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return "错误：命令执行超时（>" + timeoutSeconds + "s）已被终止";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            proc.getInputStream().transferTo(out);
            String result = out.toString(StandardCharsets.UTF_8);
            int max = 50_000;
            if (result.length() > max) {
                result = result.substring(0, max) + "\n...[输出过长已截断]";
            }
            return "exit=" + proc.exitValue() + "\n" + result;
        } catch (Exception e) {
            return "错误：执行失败 - " + e.getMessage();
        }
    }
}
