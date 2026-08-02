package com.flora.ai.api.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * SSE（Server-Sent Events）解析器。
 * <p>解析 {@code text/event-stream} 流：事件按空行分割，每个事件含若干
 * {@code data:} 行（多 data 行拼接为一条消息），{@code [DONE]} 哨兵结束。
 * 流内部使用 {@code [DONE]} 而非事件结束标记。</p>
 */
public final class SseParser {

    /** 流结束哨兵。 */
    public static final String DONE = "[DONE]";

    private SseParser() {
    }

    /** 从输入流解析 SSE 事件，逐条 data 内容回调。 */
    public static void parse(InputStream in, Consumer<String> onData) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                // 事件结束：提交累积的 data
                if (data.length() > 0) {
                    String payload = data.toString();
                    data.setLength(0);
                    if (payload.equals(DONE)) {
                        return;
                    }
                    onData.accept(payload);
                }
                continue;
            }
            if (line.startsWith("data:")) {
                String value = line.substring(5);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(value);
            }
            // 其他行（event:/id:/retry:）忽略
        }
    }
}
