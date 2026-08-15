package com.flora.shell;

import com.flora.root.java.CheckUtil;

import java.util.Objects;

/**
 * 命令执行结果。
 * <p>承载状态、退出码、文本输出（普通/错误）与可选的结构化数据。命令通过本对象输出文本，
 * 不再直接写输出；框架在分派返回后把 {@link #output()} / {@link #error()}
 * 扇出到所有已挂载的输出汇。退出码供批量入口决定进程退出值。</p>
 * <p>状态（{@link Status}）区分三类结果：命令成功、命令自身的错误（如参数/业务错误）、
 * 框架/系统错误（如来源不符、未知命令、执行异常）。</p>
 */
public final class CommandResult {

    /** 结果状态。 */
    public enum Status {
        /** 命令执行成功。 */
        SUCCESS,
        /** 命令自身的错误（参数错误、业务错误等）。 */
        COMMAND_ERROR,
        /** 框架 / 系统错误（来源不符、未知命令、转发超限、执行异常等）。 */
        SYSTEM_ERROR
    }

    /** 成功的退出码。 */
    public static final int SUCCESS = 0;

    /** 失败（命令错误 / 系统错误）的统一退出码。 */
    public static final int FAILURE = 1;

    private final Status status;
    private final int exitCode;
    private final String output;
    private final String error;
    private final Object data;

    private CommandResult(Status status, int exitCode, String output, String error, Object data) {
        this.status = CheckUtil.notNull(status, "状态不能为空");
        this.exitCode = exitCode;
        this.output = output;
        this.error = error;
        this.data = data;
    }

    /**
     * @return 成功结果，状态 SUCCESS、退出码 0、无输出无数据
     */
    public static CommandResult success() {
        return new CommandResult(Status.SUCCESS, SUCCESS, null, null, null);
    }

    /**
     * @return 失败结果，状态 COMMAND_ERROR、退出码 1、无输出无数据
     */
    public static CommandResult failure() {
        return new CommandResult(Status.COMMAND_ERROR, FAILURE, null, null, null);
    }

    /**
     * @param exitCode 退出码
     * @return 携带指定退出码、状态 COMMAND_ERROR、无输出无数据的结果
     */
    public static CommandResult exit(int exitCode) {
        return new CommandResult(Status.COMMAND_ERROR, exitCode, null, null, null);
    }

    /**
     * @param data 结构化返回数据
     * @return 成功且携带结构化数据的结果
     */
    public static CommandResult data(Object data) {
        return new CommandResult(Status.SUCCESS, SUCCESS, null, null,
                CheckUtil.notNull(data, "结果数据不能为空"));
    }

    /**
     * 成功且输出一段文本的结果。
     *
     * @param output 普通输出文本（框架扇出为一行）
     * @return 成功、携带输出的结果
     */
    public static CommandResult output(String output) {
        return new CommandResult(Status.SUCCESS, SUCCESS,
                CheckUtil.notNull(output, "输出文本不能为空"), null, null);
    }

    /**
     * 命令错误且输出一段错误文本的结果。
     *
     * @param error 错误输出文本（框架扇出到错误流）
     * @return 命令错误、携带错误输出的结果
     */
    public static CommandResult error(String error) {
        return commandError(error);
    }

    /**
     * 命令自身错误且输出一段错误文本的结果。
     *
     * @param error 错误输出文本
     * @return 命令错误、携带错误输出的结果
     */
    public static CommandResult commandError(String error) {
        return new CommandResult(Status.COMMAND_ERROR, FAILURE, null,
                CheckUtil.notNull(error, "错误文本不能为空"), null);
    }

    /**
     * 框架 / 系统错误且输出一段错误文本的结果。
     *
     * @param error 错误输出文本
     * @return 系统错误、携带错误输出的结果
     */
    public static CommandResult systemError(String error) {
        return new CommandResult(Status.SYSTEM_ERROR, FAILURE, null,
                CheckUtil.notNull(error, "错误文本不能为空"), null);
    }

    /**
     * @return 结果状态
     */
    public Status status() {
        return status;
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
        return "CommandResult{status=" + status + ", exitCode=" + exitCode
                + ", output=" + Objects.toString(output) + ", error=" + Objects.toString(error)
                + ", data=" + Objects.toString(data) + '}';
    }
}
