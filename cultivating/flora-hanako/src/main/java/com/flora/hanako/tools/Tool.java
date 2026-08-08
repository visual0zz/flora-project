package com.flora.hanako.tools;

import java.util.Map;

/**
 * Agent 工具：模型可调用的能力单元（文件/终端/网页/待办等）。
 * <p>对应 openhanako {@code lib/tools/*} 的工具抽象。每个工具声明 JSON Schema 参数，
 * 由编排层下发给 LLM；执行返回文本结果回填到对话。</p>
 */
public interface Tool {

    /** 工具名（模型调用时使用的 function name）。 */
    String name();

    /** 工具描述（注入 system prompt 供模型理解用途）。 */
    String description();

    /** 入参 JSON Schema（对象结构）。 */
    Map<String, Object> parametersSchema();

    /**
     * 执行工具。
     *
     * @param args 模型传入的参数（已解析为 Map）
     * @return 工具执行结果文本（回填为 TOOL 消息）
     */
    String execute(Map<String, Object> args);
}
