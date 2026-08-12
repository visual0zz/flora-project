package com.flora.crypto.core.interfaces.material.param;

/**
 * 对称密钥参数。
 */
public interface KeyParameter extends CipherParameter {

    byte[] getKey();
}
