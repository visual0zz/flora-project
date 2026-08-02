package com.flora.ai.api.impl;

import java.util.Iterator;

/**
 * 流式事件迭代器：按序消费 {@link StreamEvent}，遇到 DONE 后 hasNext() 为 false。
 * <p>阻塞式拉取（调用方按需取），实现方内部负责缓冲与结束判定。可关闭（释放底层连接）。</p>
 */
public interface StreamIterator extends Iterator<StreamEvent>, AutoCloseable {

    /** 便捷：消费全部文本增量并拼接（跳过思考/完成事件）。 */
    default String collectText() {
        StringBuilder sb = new StringBuilder();
        while (hasNext()) {
            StreamEvent e = next();
            if (e.type() == StreamEvent.Type.TEXT && e.text() != null) {
                sb.append(e.text());
            }
        }
        return sb.toString();
    }

    @Override
    default void close() {
        // 默认空实现：无底层资源时无需关闭
    }
}
