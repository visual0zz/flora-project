package com.flora.runtime.config.interfaces;

public interface ReloadableConfig extends Config{
    void refreshWith(Config newConfig);
    void replaceWith(Config newConfig);
}
