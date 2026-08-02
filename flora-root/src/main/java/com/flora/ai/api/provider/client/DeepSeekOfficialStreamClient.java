package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.provider.protocol.DeepSeekProtocol;
import com.flora.codec.json.JsonBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * DeepSeek 流式对话客户端（增量文本/思考输出）。
 */
public final class DeepSeekOfficialStreamClient extends DeepSeekClientSupport implements StreamingClient {

    public DeepSeekOfficialStreamClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = JsonBuilder.toJsonString(DeepSeekProtocol.buildRequestMap(request,
                endpoint.modelId(), true, null));
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(url(), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            DeepSeekProtocol.Delta delta = DeepSeekProtocol.extractStreamDelta(data);
            if (delta == null) {
                return;
            }
            queue.offer(delta.thinking()
                    ? StreamEvent.thinking(delta.text()) : StreamEvent.text(delta.text()));
        });
        return new QueueStreamIterator(queue);
    }
}
