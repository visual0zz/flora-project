package com.flora.ai;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Message;
import com.flora.ai.api.ModelSpec;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.spi.AiProvider;
import com.flora.ai.spi.Endpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiApi} 门面与 mock provider 测试。
 */
class AiApiTest {

    private static final ModelSpec MOCK = ModelSpec.of("mock-model-1", "mock");

    @Test
    void loadsProvidersViaServiceLoader() {
        assertFalse(AiApi.providers().isEmpty(), "ServiceLoader 应加载到 mock provider");
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("mock")));
    }

    @Test
    void chatMatchesModel() {
        ChatClient client = AiApi.chat(MOCK, Endpoint.of("http://mock"));
        ChatRequest req = ChatRequest.builder()
                .model(MOCK)
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
        ChatResponse resp = client.chat(req);
        assertEquals("mock-answer:mock-model-1", resp.text());
        assertFalse(resp.isThinking());
    }

    @Test
    void askReturnsText() {
        ChatClient client = AiApi.chat(MOCK, Endpoint.of("http://mock"));
        ChatRequest req = ChatRequest.builder().model(MOCK)
                .message(Message.of(Message.Role.USER, "hi")).build();
        assertEquals("mock-answer:mock-model-1", client.ask(req));
    }

    @Test
    void streamingCapabilityDetectedByInstanceof() {
        ChatClient client = AiApi.chat(MOCK, Endpoint.of("http://mock"));
        assertInstanceOf(StreamingClient.class, client);
        String text = ((StreamingClient) client).stream(null).collectText();
        assertEquals("mock-answer", text);
    }

    @Test
    void streamIteratorSkipsThinkingAndDone() {
        StreamingClient sc = (StreamingClient) AiApi.chat(MOCK, Endpoint.of("http://mock"));
        var it = sc.stream(null);
        assertTrue(it.hasNext());
        assertEquals(StreamEvent.Type.TEXT, it.next().type());
        assertEquals(StreamEvent.Type.THINKING, it.next().type());
        assertEquals(StreamEvent.Type.TEXT, it.next().type());
        assertEquals(StreamEvent.Type.DONE, it.next().type());
        assertFalse(it.hasNext());
    }

    @Test
    void unsupportedModelThrows() {
        ModelSpec unknown = ModelSpec.of("some-model", "unknown-vendor");
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.chat(unknown, Endpoint.of("http://x")));
    }

    @Test
    void providersListed() {
        assertEquals("mock", AiApi.providers().get(0).name());
    }
}
