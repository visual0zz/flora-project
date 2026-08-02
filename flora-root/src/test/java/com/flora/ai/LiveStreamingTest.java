package com.flora.ai;

import com.flora.ai.AiApi;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Message;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.StreamIterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Live streaming integration tests against real vendors (network + valid API key required).
 * Both endpoints share the same DeepSeek key and are reached via the DeepSeek gateway:
 * DeepSeek direct at https://api.deepseek.com,
 * Anthropic-compatible at https://api.deepseek.com/anthropic.
 * The key is read from DEEPSEEK_API_KEY; if unset, both tests are skipped (TestAborted).
 * Tagged slow at class level: run via ./action/test-slow.cmd.
 */
@Tag("slow")
class LiveStreamingTest {

    private static final String DEEPSEEK_BASE = "https://api.deepseek.com";
    private static final String ANTHROPIC_BASE = "https://api.deepseek.com/anthropic";
    private static final String PROMPT = "Introduce yourself in one sentence.";

    @AfterEach
    void cleanup() {
        AiApi.unregister("anthropic-live");
        AiApi.unregister("deepseek-live");
        AiApi.setRouter(null);
    }

    @Test
    void anthropicStreamingLive() {
        String key = apiKey();
        String model = env("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022");
        StreamingClient client = registerAndGet("anthropic-live",
                "ANTHROPIC_OFFICIAL", model, ANTHROPIC_BASE, key);
        String message = streamAndPrint(client, "Anthropic/" + model);
        assertNotNull(message);
        assertFalse(message.isBlank());
    }

    @Test
    void deepseekStreamingLive() {
        String key = apiKey();
        String model = env("DEEPSEEK_MODEL", "deepseek-chat");
        StreamingClient client = registerAndGet("deepseek-live",
                "DEEPSEEK_OFFICIAL", model, DEEPSEEK_BASE, key);
        String message = streamAndPrint(client, "DeepSeek/" + model);
        assertNotNull(message);
        assertFalse(message.isBlank());
    }

    // -- helpers --

    /** Read the shared DeepSeek key; skip the test when not configured. */
    private static String apiKey() {
        String key = System.getenv("DEEPSEEK_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(),
                "skip: DEEPSEEK_API_KEY not set");
        return key;
    }

    private static StreamingClient registerAndGet(String id, String apiKind,
                                                  String model, String baseUrl, String key) {
        String json = "{\"id\":\"" + id + "\",\"apiKind\":\"" + apiKind
                + "\",\"modelId\":\"" + model + "\",\"baseUrl\":\"" + baseUrl
                + "\",\"apiKey\":\"" + key + "\",\"capabilities\":[\"STREAM\"]}";
        AiApi.register(json);
        return AiApi.getByName(id + ":STREAM", StreamingClient.class);
    }

    private static String streamAndPrint(StreamingClient client, String label) {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, PROMPT))
                .build();
        StringBuilder full = new StringBuilder();
        System.out.println("==== " + label + " streaming output ====");
        try (StreamIterator it = client.stream(req)) {
            while (it.hasNext()) {
                StreamEvent e = it.next();
                switch (e) {
                    case StreamEvent.Text t -> {
                        System.out.print(t.delta());
                        full.append(t.delta());
                    }
                    case StreamEvent.Thinking t -> System.out.print("[thinking] " + t.delta());
                    case StreamEvent.Done d -> System.out.println(
                            "\n[done] finishReason=" + d.finishReason() + ", usage=" + d.usage());
                    case StreamEvent.Error err -> System.out.println("\n[error] " + err.message());
                    case StreamEvent.ToolCallDelta ignored -> { /* not triggered here */ }
                }
            }
        }
        System.out.println("==== " + label + " full message ====");
        System.out.println(full);
        return full.toString();
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
