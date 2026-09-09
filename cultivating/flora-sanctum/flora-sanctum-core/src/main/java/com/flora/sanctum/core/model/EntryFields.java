package com.flora.sanctum.core.model;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 条目内置预设字段（password / url / username / labels / notes）。
 * <p>
 * 预设字段以独立块存储（块 type 为 {@code field}，fieldName 固定为预设名），
 * 不放在 entry 负载 JSON 内；createTime 与 updateTime 例外：直接存条目 JSON 内。
 * labels 不可变（{@link List#copyOf}）。
 *
 * @param password 口令（必填语义）
 * @param url     服务 URL
 * @param username 账户名
 * @param labels  标签列表（空集合视为无标签）
 */
public final class EntryFields {

    /** 全部字段为 null/空列表。 */
    public static final EntryFields EMPTY = new EntryFields(null, null, null, List.of());

    /** 预设字段名集合（独立块存储，GUI 固定显示不可删除）。 */
    public static final Set<String> PRESET_NAMES = Set.of(
            "password", "url", "username", "labels", "notes");

    private final String password;
    private final String url;
    private final String username;
    private final List<String> labels;

    public EntryFields(String password, String url, String username, List<String> labels) {
        this.password = password;
        this.url = url;
        this.username = username;
        this.labels = labels == null ? List.of() : List.copyOf(labels);
    }

    public String password() {
        return password;
    }

    public String url() {
        return url;
    }

    public String username() {
        return username;
    }

    public List<String> labels() {
        return labels;
    }

    /** 判断字段名是否为预设字段（预设字段以独立块存储且不可删除）。 */
    public static boolean isPreset(String name) {
        return name != null && PRESET_NAMES.contains(name);
    }

    /**
     * 从逗号分隔的标签字符串构造（用于 GUI 单行输入）。
     * 空字符串/空白 token 过滤；首尾空白裁剪。
     */
    public static List<String> parseLabels(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        String[] parts = csv.split(",");
        List<String> out = new java.util.ArrayList<>(parts.length);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 把 labels 列表序列化为逗号分隔字符串（用于 GUI 显示）。 */
    public static String labelsToString(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        return String.join(", ", labels);
    }

    /** 从 entry JSON 读取 labels 列表（无则空列表）。 */
    public static List<String> labelsOf(com.flora.root.codec.json.model.JsonObject entry) {
        if (entry == null) {
            return List.of();
        }
        com.flora.root.codec.json.model.JsonArray arr = entry.getArray("labels");
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>(arr.size());
        for (com.flora.root.codec.json.model.JsonValue v : arr.elements()) {
            out.add(v.asString());
        }
        return out;
    }
}