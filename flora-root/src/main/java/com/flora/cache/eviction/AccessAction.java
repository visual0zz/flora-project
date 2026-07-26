package com.flora.cache.eviction;

/**
 * 触发淘汰策略「访问 / 写」回调的动作类型。
 * <p>
 * 与 {@link com.flora.cache.CacheEventType} 的写 / 读分类一一对应，用于以单一
 * {@link EvictionPolicy#onAccess(Object, AccessAction, boolean)} 取代原先一组细碎的
 * {@code onPut / onGet / onTouch / onMutate / onAccess} 回调：
 * <ul>
 *   <li>{@link #PUT}：写入（put / putIfAbsent 生效），由 {@code existed} 区分 INSERT(新建) 与 UPDATE(覆盖)；</li>
 *   <li>{@link #GET}：读取（get），{@code existed == true} 视为命中、刷新热度，{@code false} 视为未命中；</li>
 *   <li>{@link #TOUCH}：TTL 刷新（setTtl），重新确认条目仍被需要，刷新其热度。</li>
 * </ul>
 * 命中读与写都会刷新条目热度、影响淘汰；未命中读通常仅预热频率素描、不进入淘汰候选段。
 *
 * @see EvictionPolicy#onAccess(Object, AccessAction, boolean)
 */
public enum AccessAction {

    /** 写入（put / putIfAbsent 生效）：新建或覆盖 */
    PUT,

    /** 读取（get）：命中或尚未命中 */
    GET,

    /** TTL 刷新（setTtl）：重新确认条目仍被需要 */
    TOUCH,
}
