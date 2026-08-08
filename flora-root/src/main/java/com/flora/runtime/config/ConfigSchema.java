package com.flora.runtime.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 描述配置中有哪些 key 的数据结构。
 * <p>维护一个不可变的 key 集合。key 使用点号路径（如 {@code "db.host"}），
 * 既是配置访问路径，也约定为远端键值源中的扁平键。</p>
 * <p>构造时校验 key 合法性：拒绝 null / 空串、含空路径段（如 {@code "a..b"}、
 * 首尾点号）以及前缀冲突（{@code "a.b"} 与 {@code "a.b.c"} 同时声明会导致
 * 嵌套展开时静默丢弃已声明 key 的值）。校验失败抛 {@link ConfigException}。</p>
 */
public final class ConfigSchema {

    private final Set<String> keys;

    private ConfigSchema(Set<String> keys) {
        this.keys = Collections.unmodifiableSet(keys);
    }

    /** 按声明顺序创建 schema。 */
    public static ConfigSchema of(String... keys) {
        return of(java.util.Arrays.asList(keys));
    }

    /** 按集合迭代顺序创建 schema。 */
    public static ConfigSchema of(Collection<String> keys) {
        if (keys == null) throw new ConfigException("keys 不能为 null");
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String key : keys) {
            validateKey(key);
            for (String existing : set) {
                if (isPrefixConflict(key, existing)) {
                    throw new ConfigException("schema key 前缀冲突: '" + key + "' 与 '" + existing + "'");
                }
            }
            set.add(key);
        }
        return new ConfigSchema(set);
    }

    /** 返回 schema 声明的全部 key（不可变，保持声明顺序）。 */
    public Set<String> keys() {
        return keys;
    }

    /** 检查指定 key 是否在 schema 中声明。 */
    public boolean contains(String key) {
        return keys.contains(key);
    }

    private static void validateKey(String key) {
        if (key == null || key.isEmpty()) throw new ConfigException("schema key 不能为 null 或空串");
        if (key.startsWith(".") || key.endsWith(".")) {
            throw new ConfigException("schema key 不能以点号开头或结尾: " + key);
        }
        for (String segment : key.split("\\.")) {
            if (segment.isEmpty()) {
                throw new ConfigException("schema key 不能包含空路径段: " + key);
            }
        }
    }

    /** 判断两个合法 key 是否存在前缀重叠（按点号段边界判断，避免 "ab" 与 "a.b" 误判）。 */
    private static boolean isPrefixConflict(String a, String b) {
        return a.startsWith(b + ".") || b.startsWith(a + ".");
    }

    @Override
    public String toString() {
        return keys.toString();
    }
}
