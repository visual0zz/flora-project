/**
 * 加密原语与方案框架。
 * <p>定义加密抽象角色接口（{@code com.flora.crypto.newcore}）：摘要、分组密码、流密码、
 * 非对称密码、MAC、签名、KEM、密钥派生等，并提供 JDK 引擎桥接
 * （{@code com.flora.crypto.newcore.bridge}）、纯 Java 实现
 * （{@code com.flora.crypto.newcore.impl}）、分组密码运行模式
 * （{@code com.flora.crypto.newcore.link}）、填充方案（{@code com.flora.crypto.newcore.padding}）
 * 与具体算法方案（{@code com.flora.crypto.schemes}）。</p>
 */
package com.flora.root.crypto;
