/**
 * 纯 Java 加密算法实现包。
 * <p>不依赖 JDK 加密引擎的自研实现，包括摘要（BLAKE2b、RIPEMD-160）、
 * MAC（HMac、Poly1305）、流密码（ChaCha20）、AEAD（ChaCha20-Poly1305）、
 * KDF（PBKDF2）、口令哈希（Argon2、BCrypt、Scrypt）、DRBG（HMAC-DRBG）
 * 及基于密钥协商构造的 KEM 等。JDK 桥接实现位于
 * {@code com.flora.crypto.core.bridge}。</p>
 */
package com.flora.crypto.core.impl;
