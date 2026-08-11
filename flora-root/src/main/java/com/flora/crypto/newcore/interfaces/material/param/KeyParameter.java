package com.flora.crypto.newcore.interfaces.material.param;

/**
 * 对称密钥参数。
 */
public interface KeyParameter extends CipherParameters {

    byte[] getKey();
}
