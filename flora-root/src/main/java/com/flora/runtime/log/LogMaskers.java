package com.flora.runtime.log;

import com.flora.runtime.log.impl.LogMasker;
import com.flora.runtime.log.spi.Masker;


/**
 * 预置脱敏器门面。具体实现位于 {@code impl} 包（不导出），通过此处暴露默认实例。
 */
public final class LogMaskers {

    private LogMaskers() {
    }

    /**
     * 默认规则集：覆盖 Bearer/API key/token、URL 凭据、邮箱、身份证、信用卡及长随机串。
     * 配合 {@link LogConfig#mask(Masker)} 开启全局日志脱敏。
     */
    public static final Masker DEFAULT = LogMasker.DEFAULT;
}
