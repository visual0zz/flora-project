package com.flora.runtime.config.impl;

import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.interfaces.ConfigView;

import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link ConfigView} 的懒合并实现：创建时零成本（不合并、不读取来源），
 * 首次访问（{@link #get}/{@link #getSubConfig}）时才执行一次合并并缓存结果。
 * <p>合并结果以不可变快照形式持有，仅对外暴露只读查询；子配置视图共享同一合并结果，
 * 通过 {@link #getSubConfig(String)} 链式下钻。线程安全：懒缓存为 volatile + 双重检查。</p>
 */
public final class LazyConfigView implements ConfigView {

    private final Supplier<Map<String, Object>> merger;
    private volatile Map<String, Object> merged;

    /**
     * @param merger 懒合并工厂：首次访问时执行，产出嵌套 Map 树（此后不再调用）
     */
    public LazyConfigView(Supplier<Map<String, Object>> merger) {
        this.merger = merger;
    }

    private Map<String, Object> tree() {
        Map<String, Object> m = merged;
        if (m == null) {
            synchronized (this) {
                m = merged;
                if (m == null) {
                    m = merger.get();
                    merged = m;
                }
            }
        }
        return m;
    }

    @Override
    public Object get(String path) {
        Object v = resolve(tree(), path);
        if (v instanceof Map<?, ?> sub) {
            // 子结构 → 返回可继续下钻的子视图（与 getSubConfig 语义一致）
            @SuppressWarnings("unchecked")
            Map<String, Object> subMap = (Map<String, Object>) sub;
            return new LazyConfigView(() -> subMap);
        }
        return v; // 标量 → 原值
    }

    @Override
    public ConfigView getSubConfig(String path) {
        Object v = resolve(tree(), path);
        if (v == null) {
            return null;
        }
        if (v instanceof Map<?, ?> sub) {
            @SuppressWarnings("unchecked")
            Map<String, Object> subMap = (Map<String, Object>) sub;
            return new LazyConfigView(() -> subMap);
        }
        // 对标量路径与 MapConfig 一致：报错而非返回 null
        throw new ConfigException("路径 '" + path + "' 的值不是映射类型: " + v.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static Object resolve(Map<String, Object> map, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        Object current = map;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
