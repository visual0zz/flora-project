package com.flora.ai.agent;

import java.util.*;

/**
 * ReAct 死循环检测器。
 * <p>通过检测重复的工具调用（相同名称 + 相似参数）来判断循环。</p>
 */
public class LoopDetector {

    private final int windowSize;
    private final int threshold;

    public LoopDetector(int windowSize, int threshold) {
        this.windowSize = windowSize;
        this.threshold = threshold;
    }

    public LoopDetector() {
        this(6, 3);
    }

    /** 检测最近 windowSize 步内是否有循环。 */
    public boolean isLooping(ReActTrace trace) {
        List<ReActStep> recent = trace.steps().size() <= windowSize
                ? trace.steps()
                : trace.steps().subList(trace.steps().size() - windowSize, trace.steps().size());

        Map<String, Integer> actionCount = new HashMap<>();
        for (ReActStep step : recent) {
            for (var action : step.actions()) {
                String key = action.name();
                actionCount.merge(key, 1, Integer::sum);
            }
        }

        return actionCount.values().stream().anyMatch(c -> c >= threshold);
    }
}
