package com.flora.ai.agent;

/** Agent 分裂（分叉）记录。 */
public record AgentFork(
    AgentId parent,
    AgentId child,
    String reason,
    long timestamp
) {}
