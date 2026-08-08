package com.flora.runtime.config.interfaces;

import com.flora.common.RemoteKVSource;
import com.flora.runtime.config.ConfigPriority;
import com.flora.runtime.config.ConfigSchema;

import java.nio.file.Path;

/**
 * 更新型配置链：收集来源后可调用终结方法更新既有目标（{@link #flush()}）。
 * <p>编译期限定——此接口无法调用构建型终结方法（build/buildReloadable/view），由
 * {@code ConfigUtil.refreshConfig()} / {@code replaceConfig()} / {@code refreshSystem()} / {@code replaceSystem()} 返回；
 * 读取全局单例配置请用 {@code ConfigUtil.systemConfig()}。</p>
 */
public interface ConfigUpdater {

    ConfigUpdater loadFrom(ConfigSource source);
    ConfigUpdater loadFrom(ConfigPriority priority, ConfigSource source);
    ConfigUpdater loadFromFile(Path filePath);
    ConfigUpdater loadFromFile(ConfigPriority priority, Path filePath);
    ConfigUpdater loadFromString(String str);
    ConfigUpdater loadFromString(ConfigPriority priority, String str);
    ConfigUpdater loadFromRemote(RemoteKVSource kv, ConfigSchema schema);
    ConfigUpdater loadFromRemote(ConfigPriority priority, RemoteKVSource kv, ConfigSchema schema);
    ConfigUpdater loadFromClasspath(String resource);
    ConfigUpdater loadFromClasspath(ConfigPriority priority, String resource);
    ConfigUpdater loadFromEnv(ConfigSchema schema);
    ConfigUpdater loadFromEnv(ConfigPriority priority, ConfigSchema schema);
    ConfigUpdater loadFromSystemProperties(ConfigSchema schema);
    ConfigUpdater loadFromSystemProperties(ConfigPriority priority, ConfigSchema schema);
    ConfigUpdater loadFromSubConfig(String path);
    ConfigUpdater loadFromSubConfig(ConfigPriority priority, String path);

    /** 按绑定语义更新目标：merge 为 true 时 refresh（合并），否则 replace（全量替换）。 */
    void flush();
}
