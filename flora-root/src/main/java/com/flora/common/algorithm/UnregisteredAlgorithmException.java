package com.flora.common.algorithm;

/**
 * 算法未注册异常。
 * <p>当注册中心无法在已注册的算法工厂中查找到某个算法名时抛出。</p>
 */
public class UnregisteredAlgorithmException extends RuntimeException {

    public UnregisteredAlgorithmException(String algorithmName) {
        super("Unregistered algorithm: " + algorithmName);
    }

    public UnregisteredAlgorithmException(String algorithmName, Throwable cause) {
        super("Unregistered algorithm: " + algorithmName, cause);
    }
}
