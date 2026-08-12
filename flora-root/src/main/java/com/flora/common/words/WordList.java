package com.flora.common.words;

import com.flora.java.CheckUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 单词列表加载器（Diceware 风格）。
 * <p>词表数据打包在模块资源中（每行一个词，UTF-8），本类惰性单例加载并做一致性校验
 * （字符集、行数、去重），校验失败快速失败——避免损坏的词表产出错误口令。</p>
 * <p>内置两套词表，规模均为 8192（= 2^13，软件按索引取词均匀）：
 * <ul>
 *   <li>{@link #english()}：英文小写词表（源自 heartsucker diceware 8192 词表，纯 a-z）；</li>
 *   <li>{@link #chinese()}：中文常用双字词表（源自 cfbao pinyin8k，8191 词）。</li>
 * </ul></p>
 * <p>词表同时服务两类用途：口令生成（{@link PassphraseGenerator} 随机取词）与
 * 口令/文本的 diceware 熵评估（按词匹配估算）。</p>
 */
public final class WordList {

    /** 英文词表资源路径（classpath 相对根）。 */
    public static final String EN_RESOURCE = "com/flora/common/words/en_8192.txt";

    /** 中文词表资源路径（classpath 相对根）。 */
    public static final String ZH_RESOURCE = "com/flora/common/words/zh_8192.txt";

    private static final WordList ENGLISH = new WordList(EN_RESOURCE, 8192, WordList::isEnglishWord);

    private static final WordList CHINESE = new WordList(ZH_RESOURCE, 8191, WordList::isChineseWord);

    private final String resourcePath;
    private final Predicate<String> validator;
    private final String[] words;
    private final Map<String, Integer> indexOf;
    private final List<String> wordsView;

    private WordList(String resourcePath, int expectedSize, Predicate<String> validator) {
        this.resourcePath = resourcePath;
        this.validator = validator;
        List<String> loaded = readAndValidate(expectedSize);
        this.words = loaded.toArray(String[]::new);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < words.length; i++) {
            map.put(words[i], i);
        }
        this.indexOf = map;
        this.wordsView = List.copyOf(loaded);
    }

    /** @return 英文词表单例（8192 词，纯小写字母） */
    public static WordList english() {
        return ENGLISH;
    }

    /** @return 中文词表单例（8191 词，常用汉字） */
    public static WordList chinese() {
        return CHINESE;
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

    /** 英文词校验：小写字母（含连字符词）。 */
    private static boolean isEnglishWord(String word) {
        return word.matches("[a-z-]+");
    }

    /** 中文词校验：常用汉字。 */
    private static boolean isChineseWord(String word) {
        return word.matches("[\\u4e00-\\u9fff]+");
    }

    /** 读取资源并按词表约定校验：字符集、行数、无重复。 */
    private List<String> readAndValidate(int expectedSize) {
        String text;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = WordList.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("词表资源不存在: " + resourcePath);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取词表失败: " + resourcePath, e);
        }
        List<String> result = new ArrayList<>(expectedSize);
        for (String line : text.split("\\R")) {
            String word = line.trim();
            if (word.isEmpty()) {
                continue;
            }
            if (!validator.test(word)) {
                throw new IllegalStateException("词表含非法词: '" + word + "'");
            }
            result.add(word);
        }
        if (result.size() != expectedSize) {
            throw new IllegalStateException(
                    "词表行数异常: " + resourcePath + " 期望 " + expectedSize + "，实际 " + result.size());
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
