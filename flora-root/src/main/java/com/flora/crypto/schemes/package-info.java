/**
 * 密码学方案（scheme）包：协议编排与组合框架。
 * <p>容纳密码学协议/方案（scheme）的抽象与实现：算法级协议族（如
 * {@code keyexchange} 中的密钥交换）只反映密码学行为的数学本质；组合级协议编排
 * （如认证密钥交换、安全信道）构建于算法级协议族之上，组合
 * {@code com.flora.crypto.core} 中的原语。分发由 {@link SchemeProvider} 负责。</p>
 */
package com.flora.crypto.schemes;
