package com.flora.ai.access;

import java.util.List;

/**
 * 访问决策引擎。
 * <p>纯算法：根据策略列表判断指定路径的访问级别。</p>
 */
public class AccessDecision {

    private final List<AccessPolicy> policies;

    public AccessDecision(List<AccessPolicy> policies) {
        this.policies = policies;
    }

    /** 判断指定路径的生效访问级别。 */
    public AccessLevel resolve(String path) {
        if (policies == null || policies.isEmpty()) return AccessLevel.READ_WRITE;
        AccessLevel result = AccessLevel.READ_WRITE;
        for (AccessPolicy p : policies) {
            if (PathMatcher.match(p.pathPattern(), path)) {
                if (p.level().ordinal() < result.ordinal()) {
                    result = p.level();
                }
            }
        }
        return result;
    }

    /** 检查是否可读。 */
    public boolean canRead(String path) {
        return resolve(path) != AccessLevel.INVISIBLE;
    }

    /** 检查是否可写。 */
    public boolean canWrite(String path) {
        return resolve(path) == AccessLevel.READ_WRITE;
    }
}
