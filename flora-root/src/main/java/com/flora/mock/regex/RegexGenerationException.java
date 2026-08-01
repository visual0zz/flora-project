package com.flora.mock.regex;

/**
 * 正则字符串生成异常。
 * <p>遇到不支持的语法结构（反向引用、环视、命名组、未知 Unicode 属性、
 * 非法或未闭合的量词/分组、重复上限超阈值等）时抛出，打断整个生成流程，
 * 而非静默回退。</p>
 */
public class RegexGenerationException extends RuntimeException {

    public RegexGenerationException(String message) {
        super(message);
    }

    public RegexGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
