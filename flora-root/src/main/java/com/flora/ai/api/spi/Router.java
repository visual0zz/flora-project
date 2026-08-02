package com.flora.ai.api.spi;

import com.flora.ai.api.Endpoint;

import java.util.List;

/**
 * 路由解析器：根据任务上下文（含能力信息）从候选端点中选择一个用于本次任务。
 * <p>每个候选 Endpoint 已绑定单能力（注册时展开）。用户自定义实现，读取
 * {@link TaskContext} 的自定义信息（如 {@code "capability"}/{@code "kind"}）与
 * {@link Endpoint#extra()} 做决策，返回选中的 Endpoint。返回 {@code null} 表示无匹配，
 * 由 AiApi fallback 到默认端点。</p>
 */
public interface Router {

    /** 从候选端点中选择一个；返回 null 表示无匹配（fallback 到默认端点）。 */
    Endpoint route(List<Endpoint> endpoints, TaskContext context);
}
