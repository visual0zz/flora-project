package com.flora.shell;

import com.flora.root.java.CheckUtil;

import java.util.Objects;

/**
 * 命令执行结果。
 * <p>承载退出码与可选的结构化数据。退出码供批量入口决定进程退出值；
 * 结构化数据供 AI Agent 读取机器可读结果。</p>
 */
public final class CommandResult {

    /** 成功的退出码。 */
    public static final int SUCCESS = 0;

    /** 失败（参数错误 / 业务错误）的统一退出码。 */
    public static final int FAILURE = 1;

    private final int exitCode;
    private final Object data;

    private CommandResult(int exitCode, Object data) {
        this.exitCode = exitCode;
        this.data = data;
    }

    /**
     * @return 成功结果，退出码 0、无数据
     */
    public static CommandResult success() {
        return new CommandResult(SUCCESS, null);
    }

    /**
     * @return 失败结果，退出码 1、无数据
     */
    public static CommandResult failure() {
        return new CommandResult(FAILURE, null);
    }

    /**
     * @param exitCode 退出码
     * @return 携带指定退出码、无数据的结果
     */
    public static CommandResult exit(int exitCode) {
        return new CommandResult(exitCode, null);
    }

    /**
     * @param data 结构化返回数据
     * @return 成功且携带结构化数据的结果
     */
    public static CommandResult data(Object data) {
        return new CommandResult(SUCCESS, CheckUtil.notNull(data, "结果数据不能为空"));
    }

    /**
     * @return 退出码
     */
    public int exitCode() {
        return exitCode;
    }

    /**
     * @return 结构化数据；可能为 {@code null}
     */
    public Object data() {
        return data;
    }

    @Override
    public String toString() {
        return "CommandResult{exitCode=" + exitCode + ", data=" + Objects.toString(data) + '}';
    }
}
