package com.flora.root.mock.regex.automaton;

/**
 * 正则编译异常：遇到不支持的语法结构（环视、反向引用、命名组、未知属性、
 * 非法量词、未闭合字符类/分组等）在编译期抛出，诚实失败而非静默处理。
 */
public class AutomatonException extends RuntimeException {

    public AutomatonException(String message) {
        super(message);
    }

    public AutomatonException(String message, Throwable cause) {
        super(message, cause);
    }
}
