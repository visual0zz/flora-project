package com.flora.runtime.log.spi;

/**
 * 日志脱敏契约：将文本中的敏感信息（密钥、令牌、PII 等）掩盖为不可读形式。
 * <p>
 * 纯函数语义：相同输入得到相同输出，无状态、无副作用，可在日志之外的场景复用
 * （如配置落盘前、发往 UI 前对字段脱敏）。默认实现见
 * {@link com.flora.runtime.log.LogMaskers#DEFAULT}。
 * </p>
 */
@FunctionalInterface
public interface Masker {

    /**
     * 不脱敏的实现，原样返回文本。作为默认取值，保证未开启脱敏时行为不变。
     */
    Masker NONE = text -> text;

    /**
     * 将多个脱敏器组合为一个，按入参顺序从左至右依次作用。
     * <p>null 与 {@link #NONE} 被跳过；无有效脱敏器时返回 {@link #NONE}。</p>
     *
     * @param maskers 待组合的脱敏器，允许为 null 或空
     * @return 组合后的单一脱敏器
     */
    static Masker compose(Masker... maskers) {
        Masker result = NONE;
        if (maskers != null) {
            for (Masker m : maskers) {
                if (m == null || m == NONE) {
                    continue;
                }
                Masker cur = result;
                result = text -> m.mask(cur.mask(text));
            }
        }
        return result;
    }

    /**
     * 对文本做脱敏处理。
     *
     * @param text 原始文本，可能为 null
     * @return 脱敏后的文本；text 为 null 时返回 null
     */
    String mask(String text);
}
