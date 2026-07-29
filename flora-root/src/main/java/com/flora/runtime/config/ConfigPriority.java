package com.flora.runtime.config;

/**
 * 配置来源优先级。
 * <p>加载时按优先级从低到高依次合并，高优先级的值覆盖低优先级。
 * 同一优先级内，后添加的来源覆盖先添加的。</p>
 */
public enum ConfigPriority {

    /** 最低优先级——系统默认值、兜底配置。 */
    LOWEST,

    /** 低优先级——框架层缺省值。 */
    LOW,

    /** 普通优先级——应用程序主配置（{@link ConfigLoader#addSource(ConfigSource)} 的默认值）。 */
    NORMAL,

    /** 高优先级——显式覆盖配置。 */
    HIGH,

    /** 最高优先级——必须生效的强制配置。 */
    HIGHEST
}
