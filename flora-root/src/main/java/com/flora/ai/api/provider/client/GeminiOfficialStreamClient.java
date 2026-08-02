package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.provider.protocol.GeminiProtocol;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Gemini 流式对话客户端（增量文本输出）。
 */
public final class GeminiOfficialStreamClient extends GeminiClientSupport implements StreamingClient {

    public GeminiOfficialStreamClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = GeminiProtocol.buildRequest(request);
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(url(true), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            String delta = GeminiProtocol.extractStreamDelta(data);
            if (delta != null && !delta.isEmpty()) {
                queue.offer(StreamEvent.text(delta));
            }
        });
        return new QueueStreamIterator(queue);
    }
}
