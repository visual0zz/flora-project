package com.flora.common.words;

import com.flora.java.CheckUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 英文单词列表加载器（Diceware 风格，EFF Large Wordlist）。
 * <p>词表数据打包在模块资源 {@value #RESOURCE_PATH} 中（每行一个单词，UTF-8，共 7776 词），
 * 本类惰性单例加载并做一致性校验（行数、字符集、重复），校验失败快速失败——
 * 避免损坏的词表产出错误口令或错误熵值。</p>
 * <p>词表同时服务两类用途：口令生成（{@link PassphraseGenerator} 随机取词）与
 * 口令/文本的 diceware 熵评估（按词匹配估算）。</p>
 */
public final class WordList {

    /** 词表资源路径（classpath 相对根）。 */
    public static final String RESOURCE_PATH = "com/flora/common/words/eff_large.txt";

    /** 词表预期的单词总数（EFF Large：7776 = 6^5）。 */
    public static final int EXPECTED_SIZE = 7776;

    private static final WordList INSTANCE = new WordList();

    private final String[] words;
    private final Map<String, Integer> indexOf;
    private final List<String> wordsView;

    private WordList() {
        List<String> loaded = readAndValidate();
        this.words = loaded.toArray(String[]::new);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < words.length; i++) {
            map.put(words[i], i);
        }
        this.indexOf = map;
        this.wordsView = List.copyOf(loaded);
    }

    /** @return 默认词表（EFF Large）单例 */
    public static WordList large() {
        return INSTANCE;
    }

    /** @return 词表大小 */
    public int size() {
        return words.length;
    }

    /**
     * 按索引取词。
     *
     * @param index 下标，范围 {@code [0, size())}
     * @return 单词
     */
    public String wordAt(int index) {
        CheckUtil.mustTrue(index >= 0 && index < words.length, "词表索引越界: " + index);
        return words[index];
    }

    /** @return 单词在词表中的下标；不在词表中返回 {@code -1} */
    public int indexOf(String word) {
        CheckUtil.notEmpty(word, "单词不能为空");
        return indexOf.getOrDefault(word, -1);
    }

    /** @return 单词是否在词表中 */
    public boolean contains(String word) {
        return indexOf(word) >= 0;
    }

    /** @return 全部单词（不可变视图） */
    public List<String> words() {
        return wordsView;
    }

    /** 读取资源并按词表约定校验：行数、字符集、无重复。 */
    private static List<String> readAndValidate() {
        String text;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = WordList.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("词表资源不存在: " + RESOURCE_PATH);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取词表失败: " + RESOURCE_PATH, e);
        }
        List<String> result = new ArrayList<>(EXPECTED_SIZE);
        for (String line : text.split("\\R")) {
            String word = line.trim();
            if (word.isEmpty()) {
                continue;
            }
            if (!word.matches("[a-z-]+")) {
                throw new IllegalStateException("词表含非法词: '" + word + "'");
            }
            result.add(word);
        }
        if (result.size() != EXPECTED_SIZE) {
            throw new IllegalStateException("词表行数异常: 期望 " + EXPECTED_SIZE + "，实际 " + result.size());
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (String word : result) {
            if (seen.put(word, Boolean.TRUE) != null) {
                throw new IllegalStateException("词表含重复词: '" + word + "'");
            }
        }
        return result;
    }
}
