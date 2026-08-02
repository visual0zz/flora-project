package com.flora.ai.api.provider;

import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 队列驱动的流式事件迭代器（厂商适配共享）。
 * <p>SSE 回调线程把事件放入 {@link BlockingQueue}，消费线程阻塞拉取；
 * DONE 事件仅作为终止信号，不对外暴露。超时（30s）视为流结束。</p>
 */
public final class QueueStreamIterator implements StreamIterator {

    private final BlockingQueue<StreamEvent> queue;
    private StreamEvent next;

    public QueueStreamIterator(BlockingQueue<StreamEvent> queue) {
        this.queue = queue;
    }

    @Override
    public boolean hasNext() {
        while (next == null) {
            StreamEvent e;
            try {
                e = queue.poll(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (e == null) {
                return false; // 超时视为结束
            }
            if (e.type() == StreamEvent.Type.DONE) {
                return false;
            }
            next = e;
        }
        return true;
    }

    @Override
    public StreamEvent next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        StreamEvent e = next;
        next = null;
        return e;
    }
}
