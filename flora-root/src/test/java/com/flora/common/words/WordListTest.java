package com.flora.common.words;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WordList}（EFF Large Wordlist）加载与校验测试。
 */
class WordListTest {

    private final WordList words = WordList.large();

    @Test
    void loadsExpectedSize() {
        assertEquals(7776, words.size(), "EFF Large 词表应为 7776 词");
        assertEquals(7776, WordList.EXPECTED_SIZE);
    }

    @Test
    void firstAndLastWordsMatchEfficientData() {
        assertEquals("abacus", words.wordAt(0), "词表首词应为 abacus");
        assertEquals("zoom", words.wordAt(words.size() - 1), "词表末词应为 zoom");
    }

    @Test
    void wordsAreAllLowercase() {
        for (String word : words.words()) {
            assertTrue(word.matches("[a-z-]+"), "词表含非法词: '" + word + "'");
        }
    }

    @Test
    void lookupRoundTrip() {
        String probe = "abacus";
        int idx = words.indexOf(probe);
        assertTrue(idx >= 0, "abacus 应在词表中");
        assertEquals(probe, words.wordAt(idx));
        assertTrue(words.contains(probe));
    }

    @Test
    void hyphenatedWordsAreKept() {
        // EFF Large 含 4 个带连字符的官方词，应完整保留以维持 7776 总数
        assertTrue(words.contains("t-shirt"));
        assertTrue(words.contains("drop-down"));
    }

    @Test
    void unknownWordsAreRejected() {
        assertFalse(words.contains("flora"));
        assertFalse(words.contains("abacus123"));
        assertEquals(-1, words.indexOf("zzzzzz"));
    }

    @Test
    void indexOutOfRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> words.wordAt(-1));
        assertThrows(IllegalArgumentException.class, () -> words.wordAt(words.size()));
    }
}
