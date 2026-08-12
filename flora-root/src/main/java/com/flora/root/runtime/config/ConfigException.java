package com.flora.root.runtime.config;

/**
 * 配置加载与访问过程中抛出的异常。
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
