/**
 * 加密抽象角色接口包（核心）。
 * <p>定义加密与安全原语的抽象角色：摘要、分组密码、流密码、非对称密码、
 * AEAD、MAC、KEM、密钥协商、密钥派生函数（KDF/HKDF/PBKDF2）、
 * 确定性随机数生成器（DRBG）等。
 * 角色接口（算法族接口）位于 {@code interfaces.provider} 子包，支撑性接口位于
 * {@code interfaces} 子包；参数类型位于 {@code param} 子包，密钥对类型位于
 * {@code keypair} 子包，组合器位于 {@code combinator} 子包；
 * JDK 桥接实现位于 {@code bridge}，纯 Java 实现位于 {@code impl}，
 * 分组密码模式与填充方案分别位于 {@code mode}、{@code padding} 子包。</p>
 */
package com.flora.crypto.core;
