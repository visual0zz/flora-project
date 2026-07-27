package com.flora.crypto.schemes;

/**
 * 具体加密方案（scheme）的占位空壳。
 * <p>本类仅用于确立 {@code com.flora.crypto.schemes} 包。具体的方案实现
 * （对称/非对称引擎、模式、MAC、签名器等）将在此包内逐步落地，
 * 并与 {@code com.flora.crypto.core} 中的抽象角色接口（Digest / BlockCipher / Signer …）对应。</p>
 */
public final class Schemes {

    private Schemes() {
    }
}
