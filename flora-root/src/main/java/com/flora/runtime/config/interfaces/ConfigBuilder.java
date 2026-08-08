package com.flora.runtime.config.interfaces;

import com.flora.common.KVSource;
import com.flora.runtime.config.ConfigPriority;
import com.flora.runtime.config.ConfigSchema;

import java.nio.file.Path;

/**
 * 构建型配置链：收集来源后可调用终结方法产出新配置（{@link #build()} / {@link #buildReloadable()} / {@link #view()}）。
 * <p>编译期限定——此接口无法调用更新型终结方法（flush），由 {@code ConfigUtil.newConfig()} 返回。</p>
 */
public interface ConfigBuilder {

    ConfigBuilder loadFrom(ConfigSource source);
    ConfigBuilder loadFrom(ConfigPriority priority, ConfigSource source);
    ConfigBuilder loadFromFile(Path filePath);
    ConfigBuilder loadFromFile(ConfigPriority priority, Path filePath);
    ConfigBuilder loadFromString(String str);
    ConfigBuilder loadFromString(ConfigPriority priority, String str);
    ConfigBuilder loadFromRemote(KVSource kv, ConfigSchema schema);
    ConfigBuilder loadFromRemote(ConfigPriority priority, KVSource kv, ConfigSchema schema);
    ConfigBuilder loadFromClasspath(String resource);
    ConfigBuilder loadFromClasspath(ConfigPriority priority, String resource);
    ConfigBuilder loadFromEnv(ConfigSchema schema);
    ConfigBuilder loadFromEnv(ConfigPriority priority, ConfigSchema schema);
    ConfigBuilder loadFromSystemProperties(ConfigSchema schema);
    ConfigBuilder loadFromSystemProperties(ConfigPriority priority, ConfigSchema schema);
    ConfigBuilder loadFromSubConfig(String path);
    ConfigBuilder loadFromSubConfig(ConfigPriority priority, String path);

    /** 构建静态 {@link Config}。 */
    Config build();

    /** 构建新的 {@link ReloadableConfig}。 */
    ReloadableConfig buildReloadable();

    /** 返回已收集来源的只读查询视图。 */
    ConfigView view();
}
