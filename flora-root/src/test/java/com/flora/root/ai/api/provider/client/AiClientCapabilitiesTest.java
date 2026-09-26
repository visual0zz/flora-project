package com.flora.root.ai.api.provider.client;

import com.flora.root.ai.api.ApiSchema;
import com.flora.root.ai.api.Capability;
import com.flora.root.ai.api.Endpoint;
import com.flora.root.ai.api.IOMode;
import com.flora.root.ai.api.impl.HttpTransport;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力声明回归测试：确保 {@code capabilities()} 与协议层实际能力对齐
 * （此前 OpenAI 漏 THINKING、Anthropic 漏 MULTIMODAL）。
 */
class AiClientCapabilitiesTest {

    private static Endpoint endpoint(ApiSchema kind) {
        return Endpoint.of("c", kind, "model", "http://c", "k", false, IOMode.CHAT, Map.of());
    }

    @Test
    void openAiDeclaresThinking() {
        Set<Capability> caps = new OpenAiOfficialClient(endpoint(ApiSchema.OPENAI_OFFICIAL),
                HttpTransport.create()).capabilities();
        assertTrue(caps.contains(Capability.THINKING), "OpenAI 协议层支持推理，应声明 THINKING");
    }

    @Test
    void anthropicDeclaresMultimodal() {
        Set<Capability> caps = new AnthropicOfficialClient(endpoint(ApiSchema.ANTHROPIC_OFFICIAL),
                HttpTransport.create()).capabilities();
        assertTrue(caps.contains(Capability.MULTIMODAL), "Anthropic 协议层支持 image 块，应声明 MULTIMODAL");
    }
}
