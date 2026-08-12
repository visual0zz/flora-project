package com.flora.common.words;

import com.flora.java.CheckUtil;

import java.security.SecureRandom;

/**
 * 随机英文单词口令生成器（Diceware 风格）。
 * <p>从 {@link WordList} 中均匀随机选取指定数量的单词，以分隔符拼接成口令。
 * 索引取自 {@link SecureRandom}（JDK 加密强随机源），{@code nextInt(bound)} 已做拒绝采样，无模偏差。</p>
 * <p>口令强度按词数估算：{@code 词数 × log2(词表大小)} bit，与 {@link #entropyBits(int, int)} 对应。
 * EFF Large 每词约 12.9 bit，4 词 ≈ 51.7 bit，10 词 ≈ 129 bit。</p>
 */
public final class PassphraseGenerator {

    private final WordList wordList;
    private final SecureRandom random;

    /** 使用默认词表（EFF Large）与默认随机源。 */
    public PassphraseGenerator() {
        this(WordList.large(), new SecureRandom());
    }

    /**
     * 使用指定词表与随机源。
     *
     * @param wordList 词表
     * @param random   随机源（应使用加密强随机源）
     */
    public PassphraseGenerator(WordList wordList, SecureRandom random) {
        this.wordList = CheckUtil.notNull(wordList, "词表不能为空");
        this.random = CheckUtil.notNull(random, "随机源不能为空");
    }

    /**
     * 生成口令，默认以 {@code "-"} 连接。
     *
     * @param wordCount 单词数量
     * @return 口令，如 {@code "abacus-zoom-felt-tip"}（含连字符词可能带 {@code -}）
     */
    public String generate(int wordCount) {
        return generate(wordCount, "-");
    }

    /**
     * 生成口令。
     *
     * @param wordCount 单词数量
     * @param separator 单词间分隔符（如 {@code "-"}、{@code " "}）
     * @return 口令
     */
    public String generate(int wordCount, String separator) {
        CheckUtil.mustTrue(wordCount > 0, "单词数量必须为正: " + wordCount);
        CheckUtil.notEmpty(separator, "分隔符不能为空");
        int size = wordList.size();
        StringBuilder sb = new StringBuilder(wordCount * (8 + separator.length()));
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(wordList.wordAt(random.nextInt(size)));
        }
        return sb.toString();
    }

    /**
     * 口令熵值（bit）：{@code 词数 × log2(词表大小)}。
     *
     * @param wordCount    单词数量
     * @param wordListSize 词表大小
     * @return 熵（bit）
     */
    public static double entropyBits(int wordCount, int wordListSize) {
        CheckUtil.mustTrue(wordCount > 0, "单词数量必须为正: " + wordCount);
        CheckUtil.mustTrue(wordListSize > 1, "词表大小必须大于 1: " + wordListSize);
        return wordCount * (Math.log(wordListSize) / Math.log(2));
    }
}
