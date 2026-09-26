package com.flora.root.ai.api.provider.client;

import com.flora.root.ai.api.StreamEvent;
import com.flora.root.ai.api.StreamIterator;
import com.flora.root.ai.api.impl.HttpTransport;
import com.flora.root.ai.api.provider.QueueStreamIterator;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流式客户端公共基类：统一管理 SSE 生产者线程与事件队列。
 * <p>旧实现在调用线程上同步读完整个 SSE 响应后才返回迭代器，既丧失首 token 延迟，
 * 又因队列有界（{@code ArrayBlockingQueue(64)} + 非阻塞 {@code offer}）在分片超过阈值时
 * 静默丢数据。基类改为：在独立生产者线程读取 HTTP 流，消费者通过返回的
 * {@link QueueStreamIterator} 边读边取，首 token 无需等待整段响应缓冲；队列无界，不会丢数据。</p>
 * <p>子类只需实现 {@link #handleData(String, BlockingQueue)}，把一个 SSE data 块翻译为 0..n 个
 * 事件推入队列；基类负责流结束时统一推送 {@link StreamEvent.Done}、异常时推送
 * {@link StreamEvent.Error}。子类在收到各厂商的结束哨兵（如 {@code [DONE]}）时只做收尾
 * （如 flush 工具调用），不应自行推送 {@code Done}。</p>
 */
abstract class AbstractStreamingClient {

    private static final AtomicLong SEQ = new AtomicLong();

    protected final HttpTransport http;

    protected AbstractStreamingClient(HttpTransport http) {
        this.http = http;
    }

    /** 启动流式：立即返回迭代器，生产者在后台线程填充队列。 */
    protected StreamIterator startStream(String url, Map<String, String> headers, String body) {
        BlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
        Thread producer = new Thread(() -> {
            try {
                http.streamSse(url, headers, body, data -> {
                    try {
                        handleData(data, queue);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                });
                // 正常结束：统一推送 Done（子类在结束哨兵处只做收尾，不再推送 Done）
                queue.add(new StreamEvent.Done("stop", null));
            } catch (Exception e) {
                queue.add(new StreamEvent.Error(messageOf(e)));
                queue.add(new StreamEvent.Done("error", null));
            }
        }, "ai-stream-" + SEQ.incrementAndGet());
        producer.setDaemon(true);
        producer.start();
        return new QueueStreamIterator(queue, producer);
    }

    /** 处理单个 SSE data 块，转换成事件推入队列；允许抛出 {@link InterruptedException}。 */
    protected abstract void handleData(String data, BlockingQueue<StreamEvent> queue)
            throws InterruptedException;

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null && !m.isBlank() ? m : t.getClass().getSimpleName();
    }
}
