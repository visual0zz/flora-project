/**
 * 加密抽象角色接口包（核心）。
 * <p>定义加密与安全原语的抽象角色：摘要（{@link com.flora.crypto.core.Digest}）、
 * 分组密码（{@link com.flora.crypto.core.BlockCipher}）、流密码、非对称密码、
 * AEAD、MAC、签名器、KEM、密钥协商、密钥派生函数（KDF/HKDF）、
 * 基于口令的加密参数生成器及确定性随机数生成器（DRBG）等。
 * 具体实现位于 {@code engine}、{@code mode}、{@code padding} 子包。</p>
 */
package com.flora.crypto.core;
