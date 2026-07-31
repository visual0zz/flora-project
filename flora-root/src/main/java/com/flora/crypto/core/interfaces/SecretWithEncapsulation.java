package com.flora.crypto.core.interfaces;

import java.util.Arrays;

/**
 * KEM 的封装结果：共享密钥（secret）与封装密文（encapsulation）。
 * <p>{@link #destroy()} 用于在使用后清除内存中的密钥材料，降低泄露风险。</p>
 */
public interface SecretWithEncapsulation {

    /** @return 共享密钥字节（调用 {@link #destroy()} 后返回空数组） */
    byte[] getSecret();

    /** @return 封装密文字节 */
    byte[] getEncapsulation();

    /** 清除密钥材料 */
    void destroy();
}
