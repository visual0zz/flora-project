package com.flora.sanctum.sync;

/**
 * Git 操作相关的异常。
 */
public class GitException extends RuntimeException {

    public GitException(String message) {
        super(message);
    }

    public GitException(String message, Throwable cause) {
        super(message, cause);
    }
}
