package com.flora.runtime.config.interfaces;

public interface ReloadableConfig extends Config{
    void reload(Config newConfig);
}
