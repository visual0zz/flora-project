package com.flora.crypto.schemes;

import com.flora.common.algorithm.AbstractAlgorithmFamilyRegister;
import com.flora.tag.ModuleEntry;

/**
 * 方案算法工厂注册中心。
 * <p>复用 {@link AbstractAlgorithmFamilyRegister} 提供的通用注册 / 归属校验 / 同名裁决 / 按名查询能力，
 * 作为方案域（密钥交换等）独立的算法注册表。每个实例即一个独立注册表；算法工厂须通过
 * {@link com.flora.common.algorithm.AlgorithmFactory#registerTo()} 自述注册到本类，否则注册会被拒绝。</p>
 */
@ModuleEntry
public class SchemeAlgorithmFamilyRegister extends AbstractAlgorithmFamilyRegister {

    public SchemeAlgorithmFamilyRegister() {}
}
