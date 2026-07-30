package com.flora.ai.agent;

import java.util.*;

/** ReAct 循环执行轨迹。 */
public class ReActTrace {

    private final List<ReActStep> steps = new ArrayList<>();
    private TraceStats stats;
    private ReActState state;

    public ReActTrace() {
        this.stats = new TraceStats(0, 0, 0);
        this.state = ReActState.THINKING;
    }

    public void addStep(ReActStep step) {
        steps.add(step);
        this.state = step.state();
        this.stats = stats.incrementSteps();
        if (!step.actions().isEmpty()) {
            this.stats = stats.incrementToolCalls();
        }
    }

    public List<ReActStep> steps() { return Collections.unmodifiableList(steps); }
    public TraceStats stats() { return stats; }
    public ReActState state() { return state; }
    public int size() { return steps.size(); }
}
