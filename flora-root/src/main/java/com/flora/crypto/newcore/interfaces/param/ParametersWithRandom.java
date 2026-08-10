package com.flora.crypto.newcore.interfaces.param;

import java.security.SecureRandom;

/**
 * 携带随机数源的密码参数（部分算法 / 填充需要额外随机源）。
 */
public interface ParametersWithRandom extends CipherParameters {

    CipherParameters getParameters();

    SecureRandom getRandom();
}
