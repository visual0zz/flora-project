package com.flora.shell;

import com.flora.root.java.CheckUtil;

import java.util.Objects;

/**
 * 命令执行结果。
 * <p>承载状态、退出码、文本消息与可选的结构化数据。命令通过本对象输出消息，
 * 不再直接写输出；框架在分派返回后把 {@link #message()} 交给各 sink（默认日志打印：
 * 成功记 info、错误记 error）。退出码供批量入口决定进程退出值。</p>
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
    private final String message;
    private final Object data;

    private CommandResult(Status status, int exitCode, String message, Object data) {
        this.status = CheckUtil.notNull(status, "状态不能为空");
        this.exitCode = exitCode;
        this.message = message;
        this.data = data;
    }

    /**
     * @return 成功结果，状态 SUCCESS、退出码 0、无消息无数据
     */
    public static CommandResult success() {
        return new CommandResult(Status.SUCCESS, SUCCESS, null, null);
    }

    /**
     * @return 命令错误结果，状态 COMMAND_ERROR、退出码 1、无消息无数据
     */
    public static CommandResult failure() {
        return new CommandResult(Status.COMMAND_ERROR, FAILURE, null, null);
    }

    /**
     * 成功且带一条消息的结果（默认日志按 info 打印）。
     *
     * @param message 输出消息
     * @return 成功、携带消息的结果
     */
    public static CommandResult output(String message) {
        return new CommandResult(Status.SUCCESS, SUCCESS,
                CheckUtil.notNull(message, "消息不能为空"), null);
    }

    /**
     * 命令自身错误且带一条消息的结果（默认日志按 error 打印）。
     *
     * @param message 错误消息
     * @return 命令错误、携带消息的结果
     */
    public static CommandResult commandError(String message) {
        return new CommandResult(Status.COMMAND_ERROR, FAILURE,
                CheckUtil.notNull(message, "消息不能为空"), null);
    }

    /**
     * 框架 / 系统错误且带一条消息的结果（默认日志按 error 打印）。
     *
     * @param message 错误消息
     * @return 系统错误、携带消息的结果
     */
    public static CommandResult systemError(String message) {
        return new CommandResult(Status.SYSTEM_ERROR, FAILURE,
                CheckUtil.notNull(message, "消息不能为空"), null);
    }

    /**
     * @param data 结构化返回数据
     * @return 成功且携带结构化数据的结果
     */
    public static CommandResult data(Object data) {
        return new CommandResult(Status.SUCCESS, SUCCESS, null,
                CheckUtil.notNull(data, "结果数据不能为空"));
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
     * @return 输出消息；可能为 {@code null}
     */
    public String message() {
        return message;
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
                + ", message=" + Objects.toString(message) + ", data=" + Objects.toString(data) + '}';
    }
}
