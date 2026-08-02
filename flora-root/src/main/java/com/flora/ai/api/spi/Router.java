package com.flora.ai.api.spi;

import com.flora.ai.api.ClientSpec;
import com.flora.ai.api.Endpoint;

import java.util.List;

/**
 * 路由解析器：根据任务上下文（含能力信息）从候选端点中选择端点与能力。
 * <p>用户自定义实现，读取 {@link TaskContext} 的自定义信息（如 {@code "capability"}/
 * {@code "kind"}）与 {@link Endpoint#extra()} 做决策，返回 {@link ClientSpec}
 * （选中的端点 + 能力）。返回 {@code null} 表示无匹配，由 AiApi fallback 到
 * 默认端点 + CHAT 能力。未注册 Router 时一律使用默认端点。</p>
 */
public interface Router {

    /** 根据任务上下文选端点+能力；返回 null 表示无匹配（fallback 到默认端点+CHAT）。 */
    ClientSpec route(List<Endpoint> endpoints, TaskContext context);
}
