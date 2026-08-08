package com.flora.runtime.config.interfaces;

import java.util.Map;

public interface Config extends ConfigView {
    /** 按点号路径获取值（如 {@code "a.b.c"}）；子结构返回可继续下钻的子 {@link Config}，标量返回原值，缺失返回 null。 */
    Object get(String path);

    /** 返回原始底层 Map 的不可变视图。 */
    Map<String, Object> toMapTree();
    /** 返回把嵌套树扁平化为「点号完整路径」为 key 的不可变 Map（如 {@code {db:{host:"x"}} → {db.host:"x"}}）；非映射叶子值（含 List、null）原样保留。 */
    Map<String, Object> toLongKeyMap();

    /** 检查是否为空。 */
    boolean isEmpty();
}
