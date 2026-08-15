package com.flora.shell;

import com.flora.root.java.CheckUtil;

import java.util.Objects;

/**
 * 命令执行结果。
 * <p>承载退出码、文本输出（普通/错误）与可选的结构化数据。命令通过本对象输出文本，
 * 不再直接写 {@code Invocation.out()}；框架在 {@code execute} 返回后把
 * {@link #output()} / {@link #error()} 扇出到所有已挂载的输出汇。
 * 退出码供批量入口决定进程退出值，结构化数据供 AI Agent 读取机器可读结果。</p>
 */
public final class CommandResult {

    /** 成功的退出码。 */
    public static final int SUCCESS = 0;

    /** 失败（参数错误 / 业务错误）的统一退出码。 */
    public static final int FAILURE = 1;

    private final int exitCode;
    private final String output;
    private final String error;
    private final Object data;

    private CommandResult(int exitCode, String output, String error, Object data) {
        this.exitCode = exitCode;
        this.output = output;
        this.error = error;
        this.data = data;
    }

    /**
     * @return 成功结果，退出码 0、无输出无数据
     */
    public static CommandResult success() {
        return new CommandResult(SUCCESS, null, null, null);
    }

    /**
     * @return 失败结果，退出码 1、无输出无数据
     */
    public static CommandResult failure() {
        return new CommandResult(FAILURE, null, null, null);
    }

    /**
     * @param exitCode 退出码
     * @return 携带指定退出码、无输出无数据的结果
     */
    public static CommandResult exit(int exitCode) {
        return new CommandResult(exitCode, null, null, null);
    }

    /**
     * @param data 结构化返回数据
     * @return 成功且携带结构化数据的结果
     */
    public static CommandResult data(Object data) {
        return new CommandResult(SUCCESS, null, null, CheckUtil.notNull(data, "结果数据不能为空"));
    }

    /**
     * 成功且输出一段文本的结果。
     *
     * @param output 普通输出文本（框架扇出为一行）
     * @return 成功、携带输出的结果
     */
    public static CommandResult output(String output) {
        return new CommandResult(SUCCESS, CheckUtil.notNull(output, "输出文本不能为空"), null, null);
    }

    /**
     * 失败且输出一段错误文本的结果。
     *
     * @param error 错误输出文本（框架扇出到错误流）
     * @return 失败、携带错误输出的结果
     */
    public static CommandResult error(String error) {
        return new CommandResult(FAILURE, null, CheckUtil.notNull(error, "错误文本不能为空"), null);
    }

    /**
     * @return 退出码
     */
    public int exitCode() {
        return exitCode;
    }

    /**
     * @return 普通输出文本；可能为 {@code null}
     */
    public String output() {
        return output;
    }

    /**
     * @return 错误输出文本；可能为 {@code null}
     */
    public String error() {
        return error;
    }

    /**
     * @return 结构化数据；可能为 {@code null}
     */
    public Object data() {
        return data;
    }

    @Override
    public String toString() {
        return "CommandResult{exitCode=" + exitCode + ", output=" + Objects.toString(output)
                + ", error=" + Objects.toString(error) + ", data=" + Objects.toString(data) + '}';
    }
}
