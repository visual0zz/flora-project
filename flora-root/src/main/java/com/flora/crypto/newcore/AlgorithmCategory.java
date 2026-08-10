package com.flora.crypto.newcore;

import com.flora.crypto.newcore.interfaces.AlgorithmFactory;

public enum AlgorithmCategory {
    DIGEST("摘要算法",null),
    MAC("消息认证码",null),
    ;
    private String desc;
    private Class<? extends AlgorithmFactory<?>> factoryClass;
    AlgorithmCategory(String desc, Class<? extends AlgorithmFactory<?>> factoryClass){
        this.desc = desc;
        this.factoryClass = factoryClass;
    }
}
