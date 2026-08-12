package com.flora.shell;

import java.util.List;

/**
 * 批量入口专属特化：argv 级前置校验 / 定制错误输出。
 * <p>实现此接口只对批量入口生效；{@code beforeExecute} 返回 {@code null} 表示通过，
 * 返回非 null 字符串表示错误消息（框架据此报错并置非零退出码）。</p>
 */
public interface CliCommand extends Command {
    /**
     * @param rawArgs 原始 argv（不含命令名）
     * @return {@code null} 表示校验通过；否则返回错误消息
     */
    default String beforeExecute(List<String> rawArgs) {
        return null;
    }
}
