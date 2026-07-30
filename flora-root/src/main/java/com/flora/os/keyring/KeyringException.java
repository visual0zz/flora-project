package com.flora.os.keyring;

import java.io.IOException;

/** 密钥存储操作异常。 */
public class KeyringException extends IOException {

    public KeyringException(String message) {
        super(message);
    }

    public KeyringException(String message, Throwable cause) {
        super(message, cause);
    }
}
