package com.flora.root.crypto.schemes.keyexchange;

import com.flora.root.crypto.schemes.Scheme;
import com.flora.root.crypto.schemes.SchemeContext;
import com.flora.root.crypto.schemes.SchemeException;

/**
 * 密钥交换（Key Exchange）协议族接口。
 * <p>只反映"密钥交换"这一数学行为的本质：双方各贡献一个公开值/密文，最终算出共享密钥材料。
 * 不绑定任何具体协议（SSH/TLS）的传输、报文、exchange hash、版本串或对端认证——
 * 这些由构建于本接口之上的组合级编排负责。</p>
 *
 * <h3>通用性</h3>
 * <p>采用多轮「贡献 → 贡献 / 共享密钥」模型，支持对称（DH/ECDH/X25519）、
 * 非对称（后量子 KEM-KEX）及任何未来数学本质的密钥交换：</p>
 * <ul>
 *   <li>首轮调用 {@link #step(byte[])} 传入 {@code null}，返回本端公开贡献（发起方第一条消息）；</li>
 *   <li>后续轮传入对端贡献：算出共享密钥并标记完成。若本端贡献尚未发出（即本端为响应方），
 *       则在本步一并返回本端贡献；若本端贡献已在首轮发出（即本端为发起方），则返回 {@code null}；</li>
 *   <li>完成后 {@link #sharedSecret()} 可用。角色（initiator/responder）不强制进接口：
 *       谁先 {@code step(null)} 谁即发起方。</li>
 * </ul>
 * <p>角色（initiator/responder）不强制进接口：谁先 {@code step(null)} 谁即发起方。</p>
 */
public interface KeyExchange extends Scheme {

    /**
     * 注入运行环境。
     *
     * @param ctx 运行环境（至少含熵源）
     */
    void init(SchemeContext ctx);

    /**
     * 推进一轮密钥交换。
     *
     * @param peerContribution 对端上一轮的公共贡献（线格式字节）；首轮传 {@code null}
     * @return 本端本轮应发送给对端的公共贡献；完成轮返回 {@code null}
     * @throws SchemeException 若协商失败或底层运算异常
     */
    byte[] step(byte[] peerContribution);

    /** @return 是否已完成（所有轮次结束） */
    boolean isComplete();

    /**
     * 最终共享密钥材料（原始，未经任何协议层 KDF/哈希）。
     *
     * @return 共享密钥字节
     */
    byte[] sharedSecret();
}
