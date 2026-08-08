package com.flora.common;

/**
 * 远程存储后端契约（Redis 等），键与值均为 {@code String}，与 Redis 协议对应。
 * <p>定义底层远程存储的最小操作面，由远程缓存（{@code RemoteCache.of(this)}）代理使用。
 * 远端过期与淘汰由后端管理，故本接口不承载容量、淘汰策略与本地过期扫描。</p>
 * <p>方法约定（全部对齐 Redis 命令语义）：</p>
 * <ul>
 *   <li>{@code ttlMillis}：{@code -1} = 持久化（对应 Redis 的「无 PX」/ {@code PERSIST}，亦对应公开 API 的
 *       {@code Duration.MAX}）；{@code > 0} = 设置该毫秒级过期（{@code PEXPIRE}）；
 *       {@code <= 0} 且 {@code != -1}（即 0 或负）= 立即删除该键（与 Redis 非正过期即删除一致）。</li>
 *   <li>{@link #ttl(String)}：键缺失返回 {@code -2}；存在但无过期返回 {@code -1}；其余返回剩余毫秒（{@code > 0}）。</li>
 *   <li>{@link #delete(String)}：返回被删除的键数量（Redis {@code DEL}，单键为 0 或 1）。</li>
 *   <li>{@link #exists(String)}：返回键是否存在（Redis {@code EXISTS}，1/0）。</li>
 * </ul>
 * <p>线程安全性取决于实现所用客户端。</p>
 */
public interface RemoteTtlKVStore extends KVSource {

    /** 写入键值（对应 Redis {@code SET}）：{@code ttlMillis} 约定见类级说明。 */
    void set(String key, String value, long ttlMillis);

    /** 仅当 key 不存在时写入（对应 Redis {@code SET key value NX [PX ttlMillis]}）；返回是否写入成功。 */
    boolean setNx(String key, String value, long ttlMillis);

    /** 刷新 key 的过期时长（对应 Redis {@code PEXPIRE} / {@code PERSIST}）；返回键是否存在。 */
    boolean expire(String key, long ttlMillis);

    /**
     * 原子「读取并续期」：返回 key 当前值（缺失返回 {@code null}），若键存在则把过期时长
     * 刷新为 {@code ttlMillis}（约定见类级说明）。
     * <p>对应 Redis 的 {@code GETEX}，实现应保证原子性
     * （如以 Lua 脚本或事务执行），供远程缓存实现滑动续期语义。</p>
     *
     * @param key       键
     * @param ttlMillis 新的过期毫秒（{@code -1} = 持久化）
     * @return 键的当前值；键缺失返回 {@code null}
     */
    String getAndExpire(String key, long ttlMillis);

    /** 查询剩余过期毫秒（Redis 语义，约定见类级说明）。 */
    long ttl(String key);

    /** 删除 key（对应 Redis {@code DEL}）；返回被删除的键数量。 */
    long delete(String key);

    /** 当前条目数量近似值。 */
    long size();

    /** 清空所有条目。 */
    void clear();
}
