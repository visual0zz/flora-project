package com.flora.ai.agent;

/** ReAct 循环终止条件。纯数据。 */
public record StopCondition(
    int maxSteps,
    int maxTokens,
    int maxEmptyCycles
) {
    public static final StopCondition DEFAULT = new StopCondition(25, 128_000, 3);
}
