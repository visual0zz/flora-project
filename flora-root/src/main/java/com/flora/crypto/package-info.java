/**
 * 加密原语与方案框架。
 * <p>定义加密抽象角色接口（{@code com.flora.crypto.core}）：摘要、分组密码、流密码、
 * 非对称密码、MAC、签名、KEM、密钥派生等，并提供 JDK 引擎适配
 * （{@code com.flora.crypto.core.engine}）、分组密码运行模式
 * （{@code com.flora.crypto.core.mode}）、填充方案（{@code com.flora.crypto.core.padding}）
 * 与具体算法方案（{@code com.flora.crypto.schemes}）。</p>
 */
package com.flora.crypto;
