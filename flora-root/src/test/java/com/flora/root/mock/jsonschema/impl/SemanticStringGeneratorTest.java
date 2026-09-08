package com.flora.root.mock.jsonschema.impl;

import com.flora.root.mock.jsonschema.impl.SemanticStringGenerator.Kind;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SemanticStringGenerator} 测试：字段名推断与语义取值。
 */
class SemanticStringGeneratorTest {

    private static SemanticStringGenerator generator(long seed) {
        return new SemanticStringGenerator(new RandomSupport(new Random(seed)));
    }

    @Test
    void infersSemanticKindFromFieldName() {
        assertEquals(Kind.EMAIL, SemanticStringGenerator.infer("email"));
        assertEquals(Kind.EMAIL, SemanticStringGenerator.infer("userEmail"));
        assertEquals(Kind.PHONE, SemanticStringGenerator.infer("phoneNumber"));
        assertEquals(Kind.DATETIME, SemanticStringGenerator.infer("createdAt"));
        assertEquals(Kind.USERNAME, SemanticStringGenerator.infer("userName"));
        assertEquals(Kind.CITY, SemanticStringGenerator.infer("city"));
        assertEquals(Kind.UUID, SemanticStringGenerator.infer("uuid"));
        assertNull(SemanticStringGenerator.infer(""));
        assertNull(SemanticStringGenerator.infer(null));
    }

    @Test
    void shortKeysDoNotMatchBySubstring() {
        // no/day 这类短词只在整词命中时生效，不参与整名包含兜底
        assertNull(SemanticStringGenerator.infer("snow"));
        assertNull(SemanticStringGenerator.infer("weekday"));
        assertEquals(Kind.CODE, SemanticStringGenerator.infer("no"));
    }

    @Test
    void generatesValueByFieldName() {
        assertTrue(generator(5).generate("email").contains("@"));
        assertTrue(generator(5).generate("phone").matches("1[35789]\\d{9}"));
        assertTrue(generator(5).generate("createdAt")
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
    }

    @Test
    void unknownFieldFallsBackToRandomString() {
        String value = generator(3).generate("zzzUnknown");
        assertNotNull(value);
        assertTrue(value.length() >= 4 && value.length() <= 12, "退化值应为短随机串: " + value);
    }

    @Test
    void everyKindProducesNonEmptyValue() {
        for (Kind kind : Kind.values()) {
            String value = generator(1).value(kind);
            assertNotNull(value, kind + " 应能取值");
            assertFalse(value.isEmpty(), kind + " 不应取到空串");
        }
    }

    @Test
    void uuidIsReproducibleFromSeed() {
        String first = generator(7).value(Kind.UUID);
        String second = generator(7).value(Kind.UUID);
        assertEquals(first, second, "同种子应取到相同 uuid");
        assertTrue(first.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "uuid 形态: " + first);
    }
}
