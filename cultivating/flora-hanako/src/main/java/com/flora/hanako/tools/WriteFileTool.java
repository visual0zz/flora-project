package com.flora.hanako.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写文件工具：将文本写入工作目录内的文件（自动建父目录）。
 * <p>复刻 openhanako 文件读写工具；同样受 PathGuard 工作目录白名单约束。</p>
 */
public final class WriteFileTool implements Tool {

    private final Path workDir;

    public WriteFileTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "将文本内容写入指定相对路径文件，父目录不存在会自动创建。用于产出文件或保存草稿。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", ReadFileTool.strProp("要写入的文件相对路径"));
        props.put("content", ReadFileTool.strProp("要写入的完整文本内容"));
        return ReadFileTool.objSchema(props, List.of("path", "content"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String p = ReadFileTool.asString(args.get("path"));
        String content = ReadFileTool.asString(args.get("content"));
        if (p == null || p.isBlank()) {
            return "错误：缺少 path 参数";
        }
        Path target = workDir.resolve(p).normalize();
        if (!target.startsWith(workDir.normalize())) {
            return "错误：拒绝写入工作目录之外的路径（PathGuard 默认拒绝）";
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
            return "已写入 " + p + "（" + (content == null ? 0 : content.length()) + " 字符）";
        } catch (IOException e) {
            return "错误：写入失败 - " + e.getMessage();
        }
    }
}
