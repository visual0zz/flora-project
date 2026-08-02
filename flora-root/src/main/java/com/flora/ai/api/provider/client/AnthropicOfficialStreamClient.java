package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.provider.protocol.AnthropicProtocol;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Anthropic 流式对话客户端（增量文本/思考输出）。
 */
public final class AnthropicOfficialStreamClient extends AnthropicClientSupport implements StreamingClient {

    public AnthropicOfficialStreamClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = AnthropicProtocol.buildRequest(request, endpoint.modelId(), true);
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(url(), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            AnthropicProtocol.Delta delta = AnthropicProtocol.extractStreamDelta(data);
            if (delta == null) {
                return;
            }
            queue.offer(delta.thinking()
                    ? StreamEvent.thinking(delta.text()) : StreamEvent.text(delta.text()));
        });
        return new QueueStreamIterator(queue);
    }
}
