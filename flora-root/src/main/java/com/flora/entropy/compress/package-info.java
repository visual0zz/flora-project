/**
 * 压缩算法包。
 * <p>提供压缩引擎统一接口 {@link com.flora.entropy.compress.Compressor}
 * 与注册分发中心 {@link com.flora.entropy.compress.CompressorProvider}，
 * 复用 {@code crypto.core} 的 AlgorithmFamily + Provider 架构模式。
 * 具体实现位于 {@code com.flora.entropy.compress.engine} 子包。</p>
 */
package com.flora.entropy.compress;
