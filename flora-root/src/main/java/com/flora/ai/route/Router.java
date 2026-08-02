package com.flora.ai.route;

import com.flora.ai.api.RegisteredModel;

import java.util.List;

/**
 * 路由解析器：从候选注册模型中选择一个用于本次任务。
 * <p>用户自定义实现，读取 {@link RegisteredModel#extra()}（附加参数）与
 * {@link TaskContext}（任务信息）做决策。返回 {@code null} 表示无匹配，
 * 由 AiApi fallback 到默认模型。未注册 Router 时一律使用默认模型。</p>
 */
public interface Router {

    /** 从候选注册模型中选择一个；返回 null 表示无匹配（fallback 到默认）。 */
    RegisteredModel route(List<RegisteredModel> models, TaskContext context);
}
