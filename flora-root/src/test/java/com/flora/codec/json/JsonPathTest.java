package com.flora.codec.json;

import com.flora.codec.JsonUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonPath RFC 9535 功能测试。
 */
class JsonPathTest {

    private static Map<String, Object> root;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setup() {
        root = new LinkedHashMap<>();
        Map<String, Object> store = new LinkedHashMap<>();
        root.put("store", store);

        List<Map<String, Object>> books = List.of(
                map("title", "Sayings of the Century", "price", 8.95, "category", "reference"),
                map("title", "Sword of Honour", "price", 12.99, "category", "fiction"),
                map("title", "Moby Dick", "price", 8.99, "isbn", "0-553-21311-3", "category", "fiction"),
                map("title", "The Lord of the Rings", "price", 22.99, "isbn", "0-395-19395-8", "category", "fiction")
        );
        store.put("book", books);
        store.put("bicycle", map("color", "red", "price", 19.95));

        List<Map<String, Object>> authors = List.of(
                map("name", "Nigel Rees", "works", 5),
                map("name", "Evelyn Waugh", "works", 8),
                map("name", "Herman Melville", "works", 11),
                map("name", "J. R. R. Tolkien", "works", 10)
        );
        root.put("authors", authors);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ===================== 基本路径 =====================

    @Test
    void rootReturnsRoot() {
        assertSame(root, JsonUtil.eval(root, "$"));
    }

    @Test
    void simpleDotPath() {
        assertEquals("red", JsonUtil.eval(root, "$.store.bicycle.color"));
    }

    @Test
    void bracketNotation() {
        assertEquals("red", JsonUtil.eval(root, "$['store']['bicycle']['color']"));
    }

    @Test
    void mixedNotation() {
        assertEquals("red", JsonUtil.eval(root, "$['store'].bicycle['color']"));
    }

    @Test
    void arrayIndex() {
        assertEquals("Sayings of the Century",
                JsonUtil.eval(root, "$.store.book[0].title"));
    }

    @Test
    void negativeIndex() {
        assertEquals("The Lord of the Rings",
                JsonUtil.eval(root, "$.store.book[-1].title"));
    }

    // ===================== 通配符 =====================

    @Test
    void wildcardObject() {
        List<Object> colors = JsonUtil.evalAll(root, "$.store.bicycle[*]");
        assertEquals(2, colors.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void wildcardArray() {
        List<Object> prices = JsonUtil.evalAll(root, "$.store.book[*].price");
        // 4 本书的 price
        assertEquals(4, prices.size());
    }

    // ===================== 多索引与切片 =====================

    @Test
    void multiIndex() {
        List<Object> r = JsonUtil.evalAll(root, "$.store.book[0,2].title");
        assertEquals(2, r.size());
        assertEquals("Sayings of the Century", r.get(0));
        assertEquals("Moby Dick", r.get(1));
    }

    @Test
    void slicePositive() {
        List<Object> r = JsonUtil.evalAll(root, "$.store.book[0:2].title");
        assertEquals(2, r.size());
    }

    @Test
    void sliceWithStep() {
        List<Object> r = JsonUtil.evalAll(root, "$.store.book[0:3:2].title");
        assertEquals(2, r.size());
        assertEquals("Sayings of the Century", r.get(0));
        assertEquals("Moby Dick", r.get(1));
    }

    // ===================== 递归下降 =====================

    @Test
    void recursiveDescentName() {
        List<Object> prices = JsonUtil.evalAll(root, "$..price");
        // store.book[*].price (4) + store.bicycle.price (1) = 5
        assertEquals(5, prices.size());
    }

    @Test
    void recursiveDescentWildcard() {
        // 返回所有叶子节点
        List<Object> all = JsonUtil.evalAll(root, "$..*");
        assertTrue(all.size() > 10);
    }

    // ===================== 过滤器 =====================

    @Test
    void filterLessThan() {
        List<Object> cheap = JsonUtil.evalAll(root, "$.store.book[?(@.price < 10)].title");
        assertEquals(2, cheap.size());
        assertTrue(cheap.contains("Sayings of the Century"));
        assertTrue(cheap.contains("Moby Dick"));
    }

    @Test
    void filterEquals() {
        List<Object> cheap = JsonUtil.evalAll(root, "$.store.book[?(@.price == 8.95)].title");
        assertEquals(1, cheap.size());
        assertEquals("Sayings of the Century", cheap.get(0));
    }

    @Test
    void filterNotEquals() {
        List<Object> r = JsonUtil.evalAll(root, "$.store.book[?(@.price != 8.95)].title");
        assertEquals(3, r.size());
    }

    @Test
    void filterLessThanOrEqual() {
        List<Object> r = JsonUtil.evalAll(root, "$.store.book[?(@.price <= 8.99)].title");
        assertEquals(2, r.size());
    }

    @Test
    void filterGreaterThan() {
        List<Object> r = JsonUtil.evalAll(root, "$.store.book[?(@.price > 20)].title");
        assertEquals(1, r.size());
        assertEquals("The Lord of the Rings", r.get(0));
    }

    @Test
    void filterAnd() {
        List<Object> r = JsonUtil.evalAll(root,
                "$.store.book[?(@.price > 10 && @.price < 20)].title");
        assertEquals(1, r.size());
        assertEquals("Sword of Honour", r.get(0));
    }

    @Test
    void filterNot() {
        // 没有 isbn 的书（即 !@.isbn）
        List<Object> r = JsonUtil.evalAll(root, "$.store.book[?!@.isbn].title");
        assertEquals(2, r.size());
    }

    @Test
    void filterOnStringEquality() {
        List<Object> r = JsonUtil.evalAll(root,
                "$.store.book[?(@.category == 'fiction')].title");
        assertEquals(3, r.size());
    }

    // ===================== 函数 =====================

    @Test
    void functionLengthOnString() {
        List<Object> r = JsonUtil.evalAll(root,
                "$.store.book[?(length(@.title) > 10)].title");
        // "Sword of Honour"(14), "Moby Dick"(9), "The Lord of the Rings"(22), "Sayings of the Century"(24)
        // length > 10: "Sword of Honour", "The Lord of the Rings", "Sayings of the Century"
        assertEquals(3, r.size());
    }

    // ===================== 边界情况 =====================

    @Test
    void nullRootDoesNotThrow() {
        assertNull(JsonUtil.eval(null, "$"));
    }

    @Test
    void pathNotFoundReturnsNull() {
        assertNull(JsonUtil.eval(root, "$.nonexistent"));
    }

    @Test
    void emptyPathThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonUtil.eval(root, ""));
    }

    @Test
    void invalidExpressionThrows() {
        assertThrows(IllegalStateException.class,
                () -> JsonUtil.eval(root, "$."));
    }

    @Test
    void evalAllNotEmptyList() {
        List<Object> r = JsonUtil.evalAll(root, "$.store.book[0].title");
        assertEquals(1, r.size());
        assertEquals("Sayings of the Century", r.get(0));
    }

    @Test
    void evalAllEmptyListWhenNotFound() {
        assertTrue(JsonUtil.evalAll(root, "$.notfound.key").isEmpty());
    }
}
