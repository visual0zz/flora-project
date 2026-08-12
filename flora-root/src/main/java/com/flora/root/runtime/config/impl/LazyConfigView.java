package com.flora.root.runtime.config.impl;

import com.flora.root.runtime.config.interfaces.ConfigView;

import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link ConfigView} 的懒合并实现：创建时零成本（不合并、不读取来源），
 * 首次访问（{@link #get}）时才执行一次合并并缓存结果。
 * <p>合并结果以不可变快照形式持有，仅对外暴露只读查询；所有子视图共享同一根合并树
 * （携带路径前缀做相对解析），保证任意层级的占位符解释都能访问完整上下文。
 * 占位符（{@code ${key}}）不做预替换，访问到含占位符的字符串时才以
 * 「根合并树 + 环境变量 + 系统属性」为上下文动态解释（解释性追踪）。</p>
 * <p>线程安全：懒缓存为 volatile + 双重检查，以共享 {@link RootRef} 为锁。</p>
 */
public final class LazyConfigView implements ConfigView {

    /** 共享的根合并树缓存：所有子视图共用，保证只合并一次。 */
    private static final class RootRef {
        volatile Map<String, Object> value;
    }

    private final RootRef ref;
    private final Supplier<Map<String, Object>> rootMerger;
    private final String prefix;

    /** 创建根视图。 */
    public LazyConfigView(Supplier<Map<String, Object>> rootMerger) {
        this(new RootRef(), rootMerger, "");
    }

    private LazyConfigView(RootRef ref, Supplier<Map<String, Object>> rootMerger, String prefix) {
        this.ref = ref;
        this.rootMerger = rootMerger;
        this.prefix = prefix;
    }

    private Map<String, Object> root() {
        Map<String, Object> m = ref.value;
        if (m == null) {
            synchronized (ref) {
                m = ref.value;
                if (m == null) {
                    m = rootMerger.get();
                    ref.value = m;
                }
            }
        }
        return m;
    }

    private Object resolve(String path) {
        return resolveIn(root(), prefix.isEmpty() ? path : prefix + "." + path);
    }

    @Override
    public Object get(String path) {
        Object v = resolve(path);
        if (v instanceof Map<?, ?> sub) {
            // 子结构 → 返回可继续下钻的子视图
            return new LazyConfigView(ref, rootMerger, join(prefix, path));
        }
        if (v instanceof String s && s.indexOf("${") >= 0) {
            return PlaceholderResolver.resolve(s, this::lookup);
        }
        return v;
    }

    /** 占位符解释上下文：根合并树 → 环境变量 → 系统属性。 */
    private String lookup(String key) {
        Object v = resolveIn(root(), key);
        if (v != null) return String.valueOf(v);
        String env = System.getenv(key);
        if (env != null) return env;
        return System.getProperty(key);
    }

    private static String join(String prefix, String path) {
        return prefix.isEmpty() ? path : prefix + "." + path;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveIn(Map<String, Object> map, String path) {
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
