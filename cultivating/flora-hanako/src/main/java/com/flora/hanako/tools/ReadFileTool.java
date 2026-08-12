package com.flora.hanako.tools;

import com.flora.root.tag.WorkInProgress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读文件工具：读取工作目录内（PathGuard 白名单）的文件内容。
 * <p>复刻 openhanako 文件读写工具；未来接入基座能力评估 D1（四级 PathGuard）做 fail-closed 权限校验。</p>
 */
@WorkInProgress("PathGuard 沙盒当前仅做工作目录白名单，完整四级访问控制待基座能力 D1 落地")
public final class ReadFileTool implements Tool {

    private final Path workDir;

    public ReadFileTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "读取指定路径文本文件的完整内容。路径相对于工作目录，越权访问会被拒绝。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", strProp("要读取的文件相对路径"));
        return objSchema(props, List.of("path"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String p = asString(args.get("path"));
        if (p == null || p.isBlank()) {
            return "错误：缺少 path 参数";
        }
        Path target = workDir.resolve(p).normalize();
        if (!target.startsWith(workDir.normalize())) {
            return "错误：拒绝访问工作目录之外的路径（PathGuard 默认拒绝）";
        }
        if (!Files.exists(target)) {
            return "错误：文件不存在: " + p;
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            int max = 200_000;
            if (content.length() > max) {
                return content.substring(0, max) + "\n...\n[内容过长，已截断 " + max + " 字符]";
            }
            return content;
        } catch (IOException e) {
            return "错误：读取失败 - " + e.getMessage();
        }
    }

    static Map<String, Object> strProp(String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", desc);
        return m;
    }

    static Map<String, Object> objSchema(Map<String, Object> props, java.util.List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", required);
        return schema;
    }

    static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
