package com.flora.ai.agent;

import com.flora.ai.chat.ToolCall;
import com.flora.ai.chat.ToolResult;
import java.util.List;

/** ReAct 循环中的一个步骤（思考 → 行动 → 观察）。 */
public record ReActStep(
    ReActState state,
    String thought,
    List<ToolCall> actions,
    List<ToolResult> observations,
    long timestamp
) {
    public ReActStep(ReActState state, String thought) {
        this(state, thought, List.of(), List.of(), System.currentTimeMillis());
    }
}
