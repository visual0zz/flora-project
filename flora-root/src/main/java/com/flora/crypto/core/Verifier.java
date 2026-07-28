package com.flora.crypto.core;

/**
 * 仅验签角色（Bouncy Castle 风格）。
 * <p>BC 将「签名」与「验证」拆成两个角色：{@link Signer} 既能签也能验，
 * 而 {@code Verifier} 只暴露验签能力，便于在只持有公钥的代码路径上收窄接口面。
 * 本接口直接继承 {@link Signer}，不新增方法——仅作为语义标记。</p>
 */
public interface Verifier extends Signer {
}
