package com.flora.runtime.config.interfaces;

public interface ConfigView {    /** 按点号路径获取值（如 {@code "a.b.c"}），路径不存在时返回 null。 */
    Object get(String path);

    ConfigView getSubConfig(String path);
}
