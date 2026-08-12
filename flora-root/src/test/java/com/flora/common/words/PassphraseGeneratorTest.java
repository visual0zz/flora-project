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

    private final WordList words = WordList.large();
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
        // EFF Large 每词 log2(7776) ≈ 12.925 bit；4 词 ≈ 51.7 bit
        double perWord = PassphraseGenerator.entropyBits(1, 7776);
        assertEquals(12.925, perWord, 0.01);
        assertEquals(perWord * 4, PassphraseGenerator.entropyBits(4, 7776), 1e-9);
    }
}
