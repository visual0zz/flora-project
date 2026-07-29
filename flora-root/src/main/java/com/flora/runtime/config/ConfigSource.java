package com.flora.runtime.config;

/**
 * 配置来源接口。
 * <p>代表一个可独立加载的配置来源，如文件、类路径资源或字符串。</p>
 */
public interface ConfigSource {

    /**
     * 加载并解析配置。
     *
     * @return 配置映射
     * @throws ConfigException 加载或解析失败时抛出
     */
    Config load();

    /**
     * 返回该来源的描述（用于日志/调试）。
     */
    String describe();

    /**
     * 返回该来源的位置标识（用于去重和循环检测）。
     */
    String location();
}
