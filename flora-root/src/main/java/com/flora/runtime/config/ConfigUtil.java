package com.flora.runtime.config;

import com.flora.common.RemoteKVSource;
import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigSource;
import com.flora.runtime.config.interfaces.FluentConfig;
import com.flora.runtime.config.interfaces.ReloadableConfig;
import com.flora.tag.ModuleEntry;

import java.nio.file.Path;

/**
 * 配置加载的流式入口。
 *
 * <pre>{@code
 * Config config = ConfigUtil.newConfig()
 *     .load(new FileConfigSource(...))
 *     .loadFile("override.yaml")
 *     .loadString("key=val")
 *     .getMap("database");
 * }</pre>
 */
@ModuleEntry
public final class ConfigUtil {

    private ConfigUtil() {}

    /** 创建独立配置链。 */
    public static ConfigLoadHelper newConfig() {
        return null;//todo 链式构造新的Config
    }

    public static ConfigLoadHelper newReloadableConfig() {
        return null;//todo 链式构造新的ReloadableConfig
    }
    public static ConfigLoadHelper refreshConfig(ReloadableConfig config) {
        return null;//todo 链式构造新的ReloadableConfig
    }
    public static ConfigLoadHelper replaceConfig(ReloadableConfig config) {
        return null;//todo 链式构造新的ReloadableConfig
    }
    /** 使用全局单例，多处调用共享同一来源列表。 */
    public static ConfigLoadHelper system() {
        return null;//todo 链式更新全局Config，全局的配置类型固定为ReloadableConfig
    }

    /**
     * 流式配置构建器，同时也是 {@link Config}。
     * <p>通过 {@link ConfigUtil#newConfig()} 或 {@link ConfigUtil#system()} 获得实例。
     * 链式调用的结果直接可作为 {@code Config} 使用。</p>
     */
    public static final class ConfigLoadHelper {
        ConfigLoadHelper loadFrom(ConfigSource source) {
            //todo
            return null;
        }
        ConfigLoadHelper loadFromFile(Path filePath){
            //todo
            return null;
        }
        ConfigLoadHelper loadFromString(String str){
            //todo
            return null;
        }
        ConfigLoadHelper loadFromRemote(RemoteKVSource kv, ConfigSchema schema){
            //todo
            return null;
        }
        ConfigLoadHelper loadFromSubConfig(String path){
            //todo 将某个路径下面的配置加载到整体，
            /* 例如存在 com.flora.database.host = 127.0.0.1
             * 存在 com.flora.database.port = 6379
             * 存在 database.host = 192.168.0.1
             * 执行 loadFromSubConfig("com.flora")之后会变为
             *  com.flora.database.host = 127.0.0.1
             *  com.flora.database.port = 6379
             *  database.host = 127.0.0.1
             *  database.port = 6379
             */
            return null;
        }

        ConfigLoadHelper loadFrom(ConfigPriority priority, ConfigSource source) {
            //todo
            return null;
        }
        ConfigLoadHelper loadFromFile(ConfigPriority priority, Path filePath){
            //todo
            return null;
        }
        ConfigLoadHelper loadFromString(ConfigPriority priority, String str){
            //todo
            return null;
        }
        ConfigLoadHelper loadFromRemote(ConfigPriority priority, RemoteKVSource kv, ConfigSchema schema){
            //todo
            return null;
        }
        ConfigLoadHelper loadFromSubConfig(ConfigPriority priority, String path){
            //todo 将某个路径下面的配置加载到整体，
            /* 例如存在 com.flora.database.host = 127.0.0.1
             * 存在 com.flora.database.port = 6379
             * 存在 database.host = 192.168.0.1
             * 执行 loadFromSubConfig("com.flora")之后会变为
             *  com.flora.database.host = 127.0.0.1
             *  com.flora.database.port = 6379
             *  database.host = 127.0.0.1
             *  database.port = 6379
             */
            return null;
        }
        FluentConfig view() {
            //todo 返回一个FluentConfig的视图
            return null;
        }
        Config build() {
            //todo 构建最终的Config
            return null;
        }


    }
}
