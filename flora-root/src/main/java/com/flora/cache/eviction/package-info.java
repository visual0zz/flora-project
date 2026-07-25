/**
 * 淘汰策略实现：所有策略均实现 {@link com.flora.cache.EvictionPolicy}，
 * 仅基于 key 维护内部索引（LRU 链表、频率计数、Count-Min Sketch 等），
 * 与具体存储后端解耦，通过 {@code setEvictionPolicy} 自由挂载到任意
 * {@link com.flora.cache.EvictableCache}/{@link com.flora.cache.BoundedCache}。
 *
 * <p>内置策略：
 * {@code LRUEvictionPolicy}（最近最少使用）、
 * {@code FIFOEvictionPolicy}（先进先出）、
 * {@code LFUEvictionPolicy}（最不经常使用）、
 * {@code WTinyLfuEvictionPolicy}（窗口 + SLRU + 频率素描的自适应淘汰）。</p>
 *
 * @see com.flora.cache.EvictionPolicy
 */
package com.flora.cache.eviction;
