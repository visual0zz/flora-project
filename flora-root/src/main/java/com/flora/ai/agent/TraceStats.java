package com.flora.ai.agent;

/** ReAct 循环统计。 */
public record TraceStats(int totalSteps, int toolCalls, int tokensUsed) {

    public TraceStats incrementSteps() {
        return new TraceStats(totalSteps + 1, toolCalls, tokensUsed);
    }

    public TraceStats incrementToolCalls() {
        return new TraceStats(totalSteps, toolCalls + 1, tokensUsed);
    }
}
