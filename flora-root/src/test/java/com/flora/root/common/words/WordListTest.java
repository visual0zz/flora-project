package com.flora.root.common.words;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WordList}（英文/中文 8192 词表）加载与校验测试。
 */
class WordListTest {

    @Test
    void englishLoadsExpectedSize() {
        WordList words = WordList.english();
        assertEquals(8192, words.size(), "英文词表应为 8192 词");
        assertEquals("aaron", words.wordAt(0));
        assertEquals("zurich", words.wordAt(words.size() - 1));
    }

    @Test
    void englishWordsAreAllLowercase() {
        for (String word : WordList.english().words()) {
            assertTrue(word.matches("[a-z]+"), "英文词表含非法词: '" + word + "'");
        }
    }

    @Test
    void chineseLoadsExpectedSize() {
        WordList words = WordList.chinese();
        assertEquals(8192, words.size(), "中文词表应为 8192 词");
        assertEquals("阿爸", words.wordAt(0));
        assertEquals("西瓜", words.wordAt(words.size() - 1));
    }

    @Test
    void chineseWordsAreAllHan() {
        for (String word : WordList.chinese().words()) {
            assertTrue(word.matches("[\\u4e00-\\u9fff]+"), "中文词表含非法词: '" + word + "'");
        }
    }

    @Test
    void englishLookupRoundTrip() {
        WordList words = WordList.english();
        String probe = "abacus";
        int idx = words.indexOf(probe);
        assertTrue(idx >= 0, "abacus 应在英文词表中");
        assertEquals(probe, words.wordAt(idx));
        assertTrue(words.contains(probe));
    }

    @Test
    void chineseLookupRoundTrip() {
        WordList words = WordList.chinese();
        String probe = "阿爸";
        int idx = words.indexOf(probe);
        assertTrue(idx >= 0, "阿爸 应在中文词表中");
        assertEquals(probe, words.wordAt(idx));
        assertTrue(words.contains(probe));
    }

    @Test
    void unknownWordsAreRejected() {
        assertFalse(WordList.english().contains("zzzzzz"));
        assertFalse(WordList.english().contains("abacus123"));
        assertEquals(-1, WordList.english().indexOf("zzzzzz"));
    }

    @Test
    void indexOutOfRangeThrows() {
        WordList words = WordList.english();
        assertThrows(IllegalArgumentException.class, () -> words.wordAt(-1));
        assertThrows(IllegalArgumentException.class, () -> words.wordAt(words.size()));
    }
}
