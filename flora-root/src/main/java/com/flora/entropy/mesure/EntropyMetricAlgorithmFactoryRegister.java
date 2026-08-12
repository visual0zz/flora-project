package com.flora.entropy.mesure;

import com.flora.common.register.AbstractAlgorithmFactoryRegister;
import com.flora.tag.ModuleEntry;

/**
 * 熵度量算法工厂注册中心。
 * <p>复用 {@link AbstractAlgorithmFactoryRegister} 提供的通用注册 / 归属校验 / 同名裁决 / 按名查询能力，
 * 作为熵度量域独立的算法注册表。每个实例即一个独立注册表；算法工厂须通过
 * {@link com.flora.common.register.AlgorithmFactory#registerTo()} 自述注册到本类，否则注册会被拒绝。</p>
 */
@ModuleEntry
public class EntropyMetricAlgorithmFactoryRegister extends AbstractAlgorithmFactoryRegister {

    public EntropyMetricAlgorithmFactoryRegister() {}
}
