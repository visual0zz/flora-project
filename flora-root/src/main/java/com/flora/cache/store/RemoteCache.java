package com.flora.cache.store;

/**
 * 远程缓存（Redis 等）抽象基类。
 * <p>
 * 继承 {@link RemoteCacheSupport} 复用其 put/get/remove 与事件派发能力及远程钩子
 * （{@code doXxx}），本地不持有容量维度、不执行淘汰；子类用具体 Redis 客户端实现
 * {@code doXxx} 钩子即可获得完整远程缓存。示例见 {@link RemoteCacheSupport}。
 * <p>
 * 线程安全性取决于子类所用客户端实现（主流 Redis 客户端的连接池都是线程安全的）。
 *
 * @see RemoteCacheSupport
 */
public abstract class RemoteCache extends RemoteCacheSupport {

    protected RemoteCache() {
        super();
    }

    /**
     * @param namespace 命名空间前缀，可为空串；非空时所有 key 操作前自动拼接
     */
    protected RemoteCache(String namespace) {
        super(namespace);
    }
}
