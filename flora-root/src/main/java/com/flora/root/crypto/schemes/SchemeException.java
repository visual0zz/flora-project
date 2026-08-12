package com.flora.root.crypto.schemes;

/**
 * 方案层统一异常。
 * <p>协议编排与原语组合过程中的错误（参数非法、协商失败、底层运算异常等）统一抛出本异常。</p>
 */
public class SchemeException extends RuntimeException {

    public SchemeException(String message) {
        super(message);
    }

    public SchemeException(String message, Throwable cause) {
        super(message, cause);
    }
}
