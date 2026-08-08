package com.flora.runtime.config.interfaces;

import java.util.Map;

public interface Config {
    /** 按点号路径获取值（如 {@code "a.b.c"}），路径不存在时返回 null。 */
    Object get(String path);

    Config getConfig(String path);
    /** 返回原始底层 Map 的不可变视图。 */
    Map<String, Object> toMapTree();

    /** 检查是否为空。 */
    boolean isEmpty();
}
