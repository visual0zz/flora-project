package com.flora.syntax.exceptions;

/**
 * 词法/语法错误：包含发生位置的错误信息。
 * <p>所有分析器模块共享此异常，错误消息统一携带位置信息，
 * 格式为 {@code "位置 N: 描述"}。</p>
 */
public class SyntaxException extends RuntimeException {

    public SyntaxException(String message) {
        super(message);
    }

    public SyntaxException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 构造带位置的错误消息。 */
    public static SyntaxException at(int pos, String message) {
        return new SyntaxException("位置 " + pos + ": " + message);
    }
}
