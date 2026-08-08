package com.flora.common;

public interface RemoteKVSource {
    /** 读取键值（对应 Redis {@code GET}）；缺失返回 {@code null}。 */
    String get(String key);

    /** key 是否存在（对应 Redis {@code EXISTS}，1/0）。 */
    boolean exists(String key);

}
