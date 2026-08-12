package com.flora.root.ai.api.provider;

import com.flora.root.ai.api.StreamEvent;
import com.flora.root.ai.api.StreamIterator;
import com.flora.root.tag.ThreadFragile;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 队列驱动的流式事件迭代器（厂商适配共享）。
 * <p>SSE 回调线程把事件放入 {@link BlockingQueue}，消费线程阻塞拉取；
 * DONE 事件仅作为终止信号，不对外暴露。超时（30s）视为流结束。</p>
 */
@ThreadFragile("内部 next 字段可变，单消费者迭代；多线程并发迭代同一实例需外部同步")
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
            if (e instanceof StreamEvent.Done) {
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
