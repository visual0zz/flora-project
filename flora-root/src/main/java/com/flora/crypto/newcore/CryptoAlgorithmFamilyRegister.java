package com.flora.crypto.newcore;

import com.flora.common.algorithm.AbstractAlgorithmFamilyRegister;
import com.flora.tag.ModuleEntry;

/**
 * 加密算法工厂注册中心。
 * <p>复用 {@link AbstractAlgorithmFamilyRegister} 提供的通用注册 / 同名裁决 / 按名查询能力，
 * 仅作为加密域统一入口暴露。算法工厂通过 {@link #register(AlgorithmFamily)} 注册，
 * 查询时按算法名 + 目标角色接口取回对应的算法工厂。</p>
 */
@ModuleEntry
public final class CryptoAlgorithmFamilyRegister extends AbstractAlgorithmFamilyRegister {

    private CryptoAlgorithmFamilyRegister() {}
}
