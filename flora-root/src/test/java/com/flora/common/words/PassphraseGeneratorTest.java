package com.flora.common.words;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PassphraseGenerator} 随机单词口令生成测试。
 */
class PassphraseGeneratorTest {

    private final WordList words = WordList.english();
    private final PassphraseGenerator generator = new PassphraseGenerator(words, new java.security.SecureRandom());

    @Test
    void generatesRequestedWordCount() {
        String passphrase = generator.generate(4);
        assertEquals(4, passphrase.split("-").length, "4 词口令应按 '-' 分隔为 4 段");
    }

    @Test
    void allWordsComeFromWordList() {
        for (String word : generator.generate(6).split("-")) {
            assertTrue(words.contains(word), "口令词应在词表中: '" + word + "'");
        }
    }

    @Test
    void chinesePassphraseWordsComeFromChineseList() {
        PassphraseGenerator zh = new PassphraseGenerator(WordList.chinese(), new java.security.SecureRandom());
        for (String word : zh.generate(4, "-").split("-")) {
            assertTrue(WordList.chinese().contains(word), "口令词应在中文词表中: '" + word + "'");
        }
    }

    @Test
    void customSeparatorIsRespected() {
        String passphrase = generator.generate(3, " ");
        assertEquals(3, passphrase.split(" ").length, "空格分隔的 3 词口令应有 3 段");
        assertTrue(passphrase.contains(" "));
    }

    @Test
    void randomOutputsDiffer() {
        assertNotEquals(generator.generate(4), generator.generate(4), "两次生成应不同");
    }

    @Test
    void invalidWordCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(0));
    }

    @Test
    void entropyBitsMatchesDicewareFormula() {
        // 8192 词表每词 log2(8192) = 13.0 bit
        double perWord = PassphraseGenerator.entropyBits(1, 8192);
        assertEquals(13.0, perWord, 1e-9);
        assertEquals(perWord * 4, PassphraseGenerator.entropyBits(4, 8192), 1e-9);
    }
}
