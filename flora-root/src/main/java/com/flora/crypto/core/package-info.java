/**
 * 加密抽象角色接口包（核心）。
 * <p>定义加密与安全原语的抽象角色：摘要、分组密码、流密码、非对称密码、
 * AEAD、MAC、KEM、密钥协商、密钥派生函数（KDF/HKDF）、
 * 基于口令的加密参数生成器及确定性随机数生成器（DRBG）等。
 * 角色接口（算法族接口）位于 {@code interfaces.provider} 子包，支撑性接口位于
 * {@code interfaces} 子包；具体实现位于 {@code engine}、{@code mode}、{@code padding} 子包。</p>
 */
package com.flora.crypto.core;
