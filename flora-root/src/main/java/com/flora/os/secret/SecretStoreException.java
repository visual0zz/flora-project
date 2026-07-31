package com.flora.os.secret;

import java.io.IOException;

/** 密钥存储操作异常。 */
public class SecretStoreException extends IOException {

    public SecretStoreException(String message) {
        super(message);
    }

    public SecretStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
