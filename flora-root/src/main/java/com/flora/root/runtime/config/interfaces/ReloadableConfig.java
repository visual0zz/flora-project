package com.flora.root.runtime.config.interfaces;

/**
 * 可热替换的配置：持有当前配置快照，支持用新配置更新。
 * <p>{@link #replaceWith(Config)} 全量替换底层配置；{@link #refreshWith(Config)} 按合并语义更新
 * ——新配置的值覆盖旧值，未涉及的旧值保留。读取侧行为与 {@link Config} 一致（转发当前快照）。</p>
 */
public interface ReloadableConfig extends Config {

    /** 全量替换底层配置为新配置。 */
    void replaceWith(Config newConfig);

    /** 按合并语义更新：新值覆盖旧值，无新值的旧 key 保留（深度合并）。 */
    void refreshWith(Config newConfig);
}
