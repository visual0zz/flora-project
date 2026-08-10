package com.flora.crypto.newcore;

/**
 * 算法未注册异常。
 * <p>当注册中心无法解析或构造某个算法名（在提示分类与跨分类搜索下均无匹配）时抛出。</p>
 */
public class UnregisteredAlgorithmException extends RuntimeException {

    public UnregisteredAlgorithmException(String algorithmName) {
        super("Unregistered algorithm: " + algorithmName);
    }

    public UnregisteredAlgorithmException(String algorithmName, Throwable cause) {
        super("Unregistered algorithm: " + algorithmName, cause);
    }
}
