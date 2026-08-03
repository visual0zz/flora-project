/**
 * 哈希、ID 生成、概率数据结构与压缩算法包。
 * <p>提供哈希工具（{@link com.flora.entropy.HashUtil}）与哈希算法实现
 * （{@code com.flora.entropy.hash}）、ID 生成器
 * （{@link com.flora.entropy.IdUtil}、{@code com.flora.entropy.id}）
 * 以及概率型数据结构（{@code com.flora.entropy.probds}），
 * 包括布隆过滤器、布谷鸟过滤器、Count-Min Sketch、HyperLogLog。</p>
 * <p>压缩能力由 {@code com.flora.entropy.compress} 子包提供，
 * 采用与 {@code crypto.core} 相同的 AlgorithmFamily + Provider 注册分发模式。</p>
 */
package com.flora.entropy;
