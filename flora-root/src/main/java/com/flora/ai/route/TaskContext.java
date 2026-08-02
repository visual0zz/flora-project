package com.flora.ai.route;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务上下文：本次任务的相关信息，供 {@link Router} 决策。
 * <p>以属性表承载任务标签/偏好等任意信息（如 {@code "kind":"reasoning"}、
 * {@code "maxCost":0.1}），由用户自定义 Router 读取。</p>
 */
public record TaskContext(Map<String, Object> attributes) {

    public static TaskContext of(Object... kvs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvs.length; i += 2) {
            map.put(String.valueOf(kvs[i]), kvs[i + 1]);
        }
        return new TaskContext(map);
    }

    public static TaskContext empty() {
        return new TaskContext(Map.of());
    }

    /** 读取任务属性；不存在返回 null。 */
    public Object get(String key) {
        return attributes.get(key);
    }
}
