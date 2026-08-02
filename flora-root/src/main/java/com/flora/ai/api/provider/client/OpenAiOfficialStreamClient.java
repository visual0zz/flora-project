package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.JsonHelper;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.provider.protocol.OpenAiProtocol;
import com.flora.codec.json.JsonBuilder;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * OpenAI 官方流式对话客户端（增量文本输出）。
 */
public final class OpenAiOfficialStreamClient extends OpenAiClientSupport implements StreamingClient {

    public OpenAiOfficialStreamClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = JsonBuilder.toJsonString(
                OpenAiProtocol.buildRequestMap(request, endpoint.modelId(), true));
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(chatUrl(), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            Map<String, Object> chunk = com.flora.codec.json.JsonParser.parseObject(data);
            var choices = JsonHelper.asList(chunk.get("choices"));
            if (!choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) ((Map<?, ?>) choices.get(0)).get("delta");
                if (delta != null) {
                    String text = JsonHelper.str(delta.get("content"));
                    if (text != null && !text.isEmpty()) {
                        queue.offer(StreamEvent.text(text));
                    }
                    String thinking = JsonHelper.str(delta.get("reasoning_content"));
                    if (thinking != null && !thinking.isEmpty()) {
                        queue.offer(StreamEvent.thinking(thinking));
                    }
                }
            }
        });
        return new QueueStreamIterator(queue);
    }
}
