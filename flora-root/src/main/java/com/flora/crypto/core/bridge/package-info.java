/**
 * JDK 加密引擎桥接包。
 * <p>将 {@code javax.crypto} 和 {@code java.security} 的引擎包装为
 * {@code com.flora.crypto.core} 抽象角色接口的实现，包括摘要、MAC、
 * 分组密码、非对称密码、密钥协商、密钥对生成器及签名等。
 * 纯 Java 自研实现位于 {@code com.flora.crypto.core.impl}。</p>
 */
package com.flora.crypto.core.bridge;
