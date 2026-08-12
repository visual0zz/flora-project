package com.flora.shell;

/**
 * Agent 专属特化：定制工具描述 / 返回值 schema。
 * <p>实现此接口只对 Agent 接入生效；未实现时框架由声明自动生成工具描述。</p>
 */
public interface AgentToolCommand extends Command {
    /**
     * @return 该命令的 Agent 工具 schema 描述文本
     */
    default String toolSchema() {
        return "";
    }
}
