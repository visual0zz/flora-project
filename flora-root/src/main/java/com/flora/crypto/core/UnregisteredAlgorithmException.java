package com.flora.crypto.core;

/**
 * DSL 表达式无法解析到任何已注册工厂时抛出。
 * <p>语义上表示「该算法在当前角色下未注册」：仅当名字在所有角色中都不存在时抛出。
 * 与之区别：算法已注册但缺少必需参数、或参数类型不匹配时，抛出 {@link IllegalArgumentException}。</p>
 * <p>类型化查询（如 {@link CryptoProvider#digest(String)}）在未命中本角色且跨角色也无法解析时抛出本异常；
 * 需要「未注册即兜底」语义的方法（如 {@link CryptoProvider#derivationFunction(String)}）仅捕获本异常以返回占位实现，
 * 从而不会掩盖参数缺失 / 类型错误等真正的配置问题。</p>
 */
public class UnregisteredAlgorithmException extends IllegalArgumentException {

    public UnregisteredAlgorithmException(String algorithm) {
        super("Unregistered algorithm: " + algorithm);
    }
}
