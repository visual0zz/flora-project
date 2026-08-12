package com.flora.root.codec.json;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonNumber;
import com.flora.root.codec.json.model.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对 JSON 编解码修复项的回归测试：
 * JsonNumber equals/hashCode 契约、JSONPath Nodelist 不去重、
 * RFC 8259 空白约束、count() 参数约束、重复键处理。
 */
class JsonSpecComplianceTest {

    @AfterEach
    void resetStrictDuplicate() {
        JsonParser.setStrictDuplicateKeys(false);
    }

    // ====== JsonNumber equals/hashCode 契约（类型敏感，遵循 Jackson 约定）======

    @Test
    void jsonNumberEqualsIsTypeSensitive() {
        // 不同 Number 子类型即使数值相等也不相等（类型敏感，遵循 Jackson 约定），
        // 且 equals 与 hashCode 必须自洽：不等则 hashCode 可不同，相等则 hashCode 必相同。
        JsonNumber dec = new JsonNumber(new BigDecimal("1.0"));
        JsonNumber lon = new JsonNumber(1L);
        assertFalse(dec.equals(lon), "不同 Number 子类型不应相等");
        JsonNumber dec2 = new JsonNumber(new BigDecimal("1.0"));
        assertTrue(dec.equals(dec2), "同类型同值应相等");
        assertEquals(dec.hashCode(), dec2.hashCode(), "相等的 JsonNumber 必须有相等 hashCode");
    }

    @Test
    void jsonNumberEqualsSameType() {
        assertTrue(new JsonNumber(1L).equals(new JsonNumber(1L)));
        assertTrue(new JsonNumber(new BigDecimal("1.0")).equals(new JsonNumber(new BigDecimal("1.0"))));
        assertFalse(new JsonNumber(1L).equals(new JsonNumber(2L)));
    }

    @Test
    void jsonNumberHonorsHashContractInSet() {
        Set<JsonNumber> set = new HashSet<>();
        set.add(new JsonNumber(new BigDecimal("1.0")));
        // 不同类型但数值相等的元素不应被视为已包含
        assertFalse(set.contains(new JsonNumber(1L)));
        assertTrue(set.contains(new JsonNumber(new BigDecimal("1.0"))));
    }

    // ====== JSONPath Nodelist 不去重（RFC 9535）======

    @Test
    void jsonPathKeepsDuplicateValues() {
        // $[*] 在含重复值的数组上必须保留所有元素，而非去重
        var r = JsonUtil.evalAll(java.util.List.of(1, 1, 1), "$[*]");
        assertEquals(3, r.size());
    }

    @Test
    void jsonPathKeepsDuplicateNodesAcrossSteps() {
        var r = JsonUtil.evalAll(
                java.util.List.of(java.util.List.of(1, 1)), "$[*][*]");
        assertEquals(2, r.size());
    }

    // ====== RFC 8259 空白约束 ======

    @Test
    void rejectsNonRfcWhitespace() {
        assertThrows(IllegalStateException.class,
                () -> JsonParser.parse("{\u00A0\"k\":1}"));
        assertThrows(IllegalStateException.class,
                () -> JsonParser.parse("{\u2028\"k\":1}"));
    }

    @Test
    void acceptsRfcWhitespace() {
        assertEquals(Long.valueOf(1), JsonParser.parseObject("  \t\n\r {\"k\":1}  ").getLong("k"));
    }

    // ====== count() 参数约束 ======

    @Test
    void countWithoutArgumentThrows() {
        assertThrows(IllegalStateException.class,
                () -> JsonUtil.evalAll(java.util.List.of(1, 2), "$[?(count() > 0)]"));
    }

    @Test
    void countWithArgumentWorks() {
        // count 统计其参数 nodelist 的节点数（RFC 9535）：@.n 是 1 个节点
        var books = java.util.List.of(
                java.util.Map.of("n", java.util.List.of(1, 2, 3)),
                java.util.Map.of("n", java.util.List.of(1)));
        var r1 = JsonUtil.evalAll(books, "$[?(count(@.n) == 1)]");
        assertEquals(2, r1.size());
        // @.n[*] 展开为 3 个节点，count == 3
        var r2 = JsonUtil.evalAll(books, "$[?(count(@.n[*]) == 3)]");
        assertEquals(1, r2.size());
    }

    // ====== 重复键：默认后者覆盖，严格模式报错 ======

    @Test
    void duplicateKeyLastWinsByDefault() {
        JsonObject o = JsonParser.parseObject("{\"a\":1,\"a\":2}");
        assertEquals(Long.valueOf(2), o.getLong("a"));
    }

    @Test
    void strictDuplicateKeysThrows() {
        JsonParser.setStrictDuplicateKeys(true);
        assertThrows(IllegalStateException.class,
                () -> JsonParser.parseObject("{\"a\":1,\"a\":2}"));
    }

    @Test
    void strictDuplicateKeysAllowsUnique() {
        JsonParser.setStrictDuplicateKeys(true);
        JsonObject o = JsonParser.parseObject("{\"a\":1,\"b\":2}");
        assertEquals(2, o.size());
    }
}
