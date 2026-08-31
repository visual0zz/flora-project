package com.flora.sanctum.core.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 根对象 uuid 的单向推导（KEK 做密钥，见设计 02"manifest"）。
 * <p>
 * 根对象 uuid 既不写入自身块头，也不记入 manifest，而由 KEK 单向推导：
 * {@code uuid = HMAC-SHA256(KEK, "sanctum-root-object-uuid-v1")[0:16]}。
 * 同一主密码派生同一 KEK，即重算出同一根对象，创建侧与解锁侧一致（不占用任何存储）。
 * 单向意味着：拿到根对象块无法反推主密钥，也无法在离线状态下枚举某密码对应的根对象位置。
 * <p>
 * 换主密码时 KEK 变化 ⇒ 根对象 uuid 变化 ⇒ 根对象的分片路径随之改变
 * （见 {@code MasterKeyRotator}：根对象改写到新路径并删除旧路径）。
 */
public final class RootUuid {

    private static final byte[] LABEL = "sanctum-root-object-uuid-v1".getBytes(StandardCharsets.US_ASCII);

    private RootUuid() {
    }

    /**
     * 由 KEK 推导根对象 uuid（确定性、单向）。
     *
     * @param kek 主密钥派生的 KEK
     * @return 根对象 uuid
     */
    public static UUID derive(byte[] kek) {
        byte[] h;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(kek, "HmacSHA256"));
            h = mac.doFinal(LABEL);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        ByteBuffer bb = ByteBuffer.wrap(h, 0, 16).order(ByteOrder.BIG_ENDIAN);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
