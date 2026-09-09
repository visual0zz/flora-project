package com.flora.root.container.order;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RocicorpFractionalIndexTest {

    /** 对齐官方实现的示例，作为跨语言实现兼容性的回归基准。 */
    @Test
    void matchesReferenceExamples() {
        String first = RocicorpFractionalIndex.INSTANCE.between(null, null);
        assertEquals("a0", first);
        String second = RocicorpFractionalIndex.INSTANCE.between(first, null);
        assertEquals("a1", second);
        String third = RocicorpFractionalIndex.INSTANCE.between(second, null);
        assertEquals("a2", third);
        assertEquals("Zz", RocicorpFractionalIndex.INSTANCE.between(null, first));
        assertEquals("a1V", RocicorpFractionalIndex.INSTANCE.between(second, third));
    }

    @Test
    void nBetweenMatchesReferenceExamples() {
        assertEquals(List.of("a0", "a1"), RocicorpFractionalIndex.INSTANCE.nBetween(null, null, 2));
        assertEquals(List.of("a2", "a3"), RocicorpFractionalIndex.INSTANCE.nBetween("a1", null, 2));
        assertEquals(List.of("Zy", "Zz"), RocicorpFractionalIndex.INSTANCE.nBetween(null, "a0", 2));
        assertEquals(List.of("a0G", "a0V"), RocicorpFractionalIndex.INSTANCE.nBetween("a0", "a1", 2));
    }

    @Test
    void appendStaysStrictlyIncreasing() {
        String prev = RocicorpFractionalIndex.INSTANCE.first();
        for (int i = 0; i < 3000; i++) {
            String next = RocicorpFractionalIndex.INSTANCE.between(prev, null);
            assertTrue(next.compareTo(prev) > 0, next + " 应大于 " + prev);
            prev = next;
        }
    }

    @Test
    void prependStaysStrictlyDecreasing() {
        String first = RocicorpFractionalIndex.INSTANCE.first();
        for (int i = 0; i < 500; i++) {
            String inserted = RocicorpFractionalIndex.INSTANCE.between(null, first);
            assertTrue(inserted.compareTo(first) < 0, inserted + " 应小于 " + first);
            first = inserted;
        }
    }

    /**
     * 变长整数把键长增长摊到 62^n 次插入：整数部分加/减一，只在进位传播到头部时才变长。
     * 连续追加或前插数千次，键长都应保持个位数。
     */
    @Test
    void repeatedAppendsAndPrependsKeepKeysShort() {
        String tail = RocicorpFractionalIndex.INSTANCE.first();
        for (int i = 0; i < 3000; i++) {
            tail = RocicorpFractionalIndex.INSTANCE.between(tail, null);
        }
        assertTrue(tail.length() <= 4,
                "3000 次追加后键长应仍很短（变长整数），实际 " + tail.length() + " (" + tail + ")");

        String head = RocicorpFractionalIndex.INSTANCE.first();
        for (int i = 0; i < 3000; i++) {
            head = RocicorpFractionalIndex.INSTANCE.between(null, head);
        }
        assertTrue(head.length() <= 8, "3000 次前插后键长应仍是个位数，实际 " + head.length() + " (" + head + ")");
    }

    /** 反复在任意位置插入，列表应始终保持全序。 */
    @Test
    void manyInsertsKeepTotalOrder() {
        List<String> keys = new ArrayList<>();
        keys.add(RocicorpFractionalIndex.INSTANCE.first());
        for (int i = 0; i < 500; i++) {
            int at = (i * 7) % keys.size();
            String prev = at == 0 ? null : keys.get(at - 1);
            keys.add(at, RocicorpFractionalIndex.INSTANCE.between(prev, keys.get(at)));
        }
        for (int i = 1; i < keys.size(); i++) {
            assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0,
                    "位置 " + i + " 处顺序被破坏: " + keys.get(i - 1) + " / " + keys.get(i));
        }
    }

    @Test
    void nBetweenReturnsSortedDistinctKeysInsideBounds() {
        String lo = "a1";
        String hi = "a2";
        List<String> keys = RocicorpFractionalIndex.INSTANCE.nBetween(lo, hi, 20);
        assertEquals(20, keys.size());
        for (int i = 0; i < keys.size(); i++) {
            assertTrue(keys.get(i).compareTo(lo) > 0, keys.get(i) + " 应大于 " + lo);
            assertTrue(keys.get(i).compareTo(hi) < 0, keys.get(i) + " 应小于 " + hi);
            if (i > 0) {
                assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0, "nBetween 应返回升序");
            }
        }
    }

    /** jitter：同一区间的并发插入应得到不同但可比较、且都落在区间内的键。 */
    @Test
    void jitteredInsertsInSameGapProduceDistinctKeys() {
        String lo = "a1";
        String hi = "a2";
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String key = RocicorpFractionalIndex.JITTERED.between(lo, hi);
            assertTrue(key.compareTo(lo) > 0, key + " 应大于 " + lo);
            assertTrue(key.compareTo(hi) < 0, key + " 应小于 " + hi);
            assertTrue(RocicorpFractionalIndex.INSTANCE.isValid(key), "生成的键应合法: " + key);
            seen.add(key);
        }
        assertTrue(seen.size() > 1, "同一区间的多次插入应产生不同的键");
    }

    @Test
    void deterministicInsertsAreStable() {
        assertEquals(RocicorpFractionalIndex.INSTANCE.between("a1", "a2"), RocicorpFractionalIndex.INSTANCE.between("a1", "a2"));
    }

    @Test
    void validatesKeys() {
        assertTrue(RocicorpFractionalIndex.INSTANCE.isValid("a0"));
        assertTrue(RocicorpFractionalIndex.INSTANCE.isValid("a1"));
        assertTrue(RocicorpFractionalIndex.INSTANCE.isValid("Zz"));
        assertFalse(RocicorpFractionalIndex.INSTANCE.isValid(null));
        assertFalse(RocicorpFractionalIndex.INSTANCE.isValid(""));
        assertFalse(RocicorpFractionalIndex.INSTANCE.isValid("0a0"), "头部必须是 A-Za-z");
        assertFalse(RocicorpFractionalIndex.INSTANCE.isValid("a"), "整数部分不完整");
        assertFalse(RocicorpFractionalIndex.INSTANCE.isValid("a10"), "小数部分不能以 0 结尾");
        assertFalse(RocicorpFractionalIndex.INSTANCE.isValid("A" + "0".repeat(26)), "最小整数不可再向前生成");
    }

    @Test
    void rejectsOutOfOrderBounds() {
        assertThrows(IllegalArgumentException.class, () -> RocicorpFractionalIndex.INSTANCE.between("a2", "a1"));
        assertThrows(IllegalArgumentException.class, () -> RocicorpFractionalIndex.INSTANCE.between("a1", "a1"));
    }

    // ====== 以下为补充测试：覆盖此前未触及的分支与边界 ======

    private static void assertStrictlyBetween(String lo, String c, String hi) {
        RocicorpFractionalIndex f = RocicorpFractionalIndex.INSTANCE;
        if (lo != null) assertTrue(f.compare(lo, c) < 0, c + " 应大于下界 " + lo);
        if (hi != null) assertTrue(f.compare(c, hi) < 0, c + " 应小于上界 " + hi);
        assertTrue(f.isValid(c), "生成的键应合法: " + c);
    }

    /** 覆盖 a==null 分支中「b 的整数部分正是最小整数」的特殊路径（无法再减，改在小数部分取中点）。 */
    @Test
    void betweenNullAndSmallestIntegerPrefixKey() {
        String smallestPrefix = "A" + "0".repeat(26);   // == SMALLEST_INTEGER
        String b = smallestPrefix + "V";                // 合法：小数 "V" 不以 0 结尾
        assertTrue(RocicorpFractionalIndex.INSTANCE.isValid(b));
        String c = RocicorpFractionalIndex.INSTANCE.between(null, b);
        assertStrictlyBetween(null, c, b);
        assertTrue(c.startsWith(smallestPrefix), "无法再向前时应在其小数部分取中点，整数部分保持最小: " + c);
        assertEquals(c, RocicorpFractionalIndex.INSTANCE.between(null, b), "确定性应可复现");
    }

    /** 覆盖 a==null 分支中「b 的整数部分为最大整数」的特殊路径（无法再加，改在小数部分取中点）。 */
    @Test
    void betweenMaxIntegerAndNull() {
        String maxInt = "z".repeat(27);                 // 最大整数，合法纯整数键
        assertTrue(RocicorpFractionalIndex.INSTANCE.isValid(maxInt));
        String c = RocicorpFractionalIndex.INSTANCE.between(maxInt, null);
        assertStrictlyBetween(maxInt, c, null);
        assertTrue(c.startsWith(maxInt), "无法再向后时应在其小数部分取中点，整数部分保持最大: " + c);
        assertEquals(c, RocicorpFractionalIndex.INSTANCE.between(maxInt, null), "确定性应可复现");
    }

    /** 跨零插入（负向末端 Zz 与正向开端 a0 之间），并锁定确定性回归值。 */
    @Test
    void betweenAcrossZeroNegativeToPositive() {
        String lo = "Zz";
        String hi = "a0";
        String c = RocicorpFractionalIndex.INSTANCE.between(lo, hi);
        assertStrictlyBetween(lo, c, hi);
        assertEquals("ZzV", c, "与官方实现字节兼容的确定性回归基准");
    }

    /** nBetween(null, b)：验证 reverse 后严格升序、互不重复、且都落在上界之前。 */
    @Test
    void nBetweenNullAndKeyIsSortedAscendingAndInsideBounds() {
        String hi = "a5";
        List<String> keys = RocicorpFractionalIndex.INSTANCE.nBetween(null, hi, 30);
        assertEquals(30, keys.size());
        assertEquals(keys.size(), new HashSet<>(keys).size(), "nBetween 应返回不重复键");
        String prev = null;
        for (String k : keys) {
            assertStrictlyBetween(prev, k, hi);
            if (prev != null) assertTrue(prev.compareTo(k) < 0, "应升序: " + prev + " / " + k);
            prev = k;
        }
    }

    /** nBetween(a, null)：验证严格升序、互不重复、且都落于下界之后。 */
    @Test
    void nBetweenKeyAndNullIsSortedAscendingAndInsideBounds() {
        String lo = "a5";
        List<String> keys = RocicorpFractionalIndex.INSTANCE.nBetween(lo, null, 30);
        assertEquals(30, keys.size());
        assertEquals(keys.size(), new HashSet<>(keys).size());
        String prev = lo;
        for (String k : keys) {
            assertStrictlyBetween(prev, k, null);
            assertTrue(prev.compareTo(k) < 0, "应升序: " + prev + " / " + k);
            prev = k;
        }
    }

    /** nBetween 在跨越零的窄区间内仍保持全序、合法、不重复。 */
    @Test
    void nBetweenInNegativeQuadrantKeepsTotalOrder() {
        String lo = "Zz";
        String hi = "a0";
        List<String> keys = RocicorpFractionalIndex.INSTANCE.nBetween(lo, hi, 40);
        assertEquals(40, keys.size());
        assertEquals(keys.size(), new HashSet<>(keys).size());
        String prev = lo;
        for (String k : keys) {
            assertStrictlyBetween(prev, k, hi);
            assertTrue(prev.compareTo(k) < 0, "应升序: " + prev + " / " + k);
            prev = k;
        }
    }

    /** 直接验证 compare 的全序契约（含 null 视为最小）。 */
    @Test
    void compareHandlesNullAndTotalOrder() {
        RocicorpFractionalIndex f = RocicorpFractionalIndex.INSTANCE;
        assertEquals(0, f.compare(null, null));
        assertEquals(0, f.compare("a1", "a1"));
        assertTrue(f.compare(null, "a0") < 0);
        assertTrue(f.compare("a0", null) > 0);
        assertTrue(f.compare("a0", "a1") < 0);
        assertTrue(f.compare("a1", "a0") > 0);
        assertTrue(f.compare("Zz", "a0") < 0, "负向应小于正向");
    }

    /** 大量生成（追加 / 前插 / 区间内中点）的每一次结果都必须是合法键。 */
    @Test
    void everyGeneratedKeyIsValid() {
        RocicorpFractionalIndex f = RocicorpFractionalIndex.INSTANCE;
        String tail = f.first();
        for (int i = 0; i < 2000; i++) {
            tail = f.between(tail, null);
            assertTrue(f.isValid(tail), "追加生成非法键: " + tail);
        }
        String head = f.first();
        for (int i = 0; i < 500; i++) {
            head = f.between(null, head);
            assertTrue(f.isValid(head), "前插生成非法键: " + head);
        }
        // 小数以 0 开头的区间（b 小数部分 "0V"），验证中点分支不产生非法键
        String a = "a00U";
        String b = "a00V";
        for (int i = 0; i < 200; i++) {
            String c = f.between(a, b);
            assertTrue(f.isValid(c), "区间内生成非法键: " + c);
            assertTrue(f.compare(a, c) < 0 && f.compare(c, b) < 0, c + " 应落在 " + a + " / " + b + " 之间");
        }
    }

    /** 抖动实例在最前哨兵、最大整数附近，仍产生合法且严格落在区间内的键。 */
    @Test
    void jitteredInsertsAtExtremesStayValidAndInBounds() {
        RocicorpFractionalIndex f = RocicorpFractionalIndex.JITTERED;
        String nearFront = "A" + "0".repeat(26) + "V";
        for (int i = 0; i < 200; i++) {
            String c = f.between(null, nearFront);
            assertTrue(f.isValid(c), "jitter 生成非法键: " + c);
            assertTrue(f.compare(c, nearFront) < 0, c + " 应小于 " + nearFront);
        }
        String nearBack = "z".repeat(27);
        for (int i = 0; i < 200; i++) {
            String c = f.between(nearBack, null);
            assertTrue(f.isValid(c), "jitter 生成非法键: " + c);
            assertTrue(f.compare(nearBack, c) < 0, c + " 应大于 " + nearBack);
        }
    }

    /** 跨越零、在极小区间内反复插入，全程保持全序且键合法（重点压测取中点分支）。 */
    @Test
    void manyInsertsSpanningNegativeAndPositiveKeepTotalOrder() {
        List<String> keys = new ArrayList<>();
        keys.add(RocicorpFractionalIndex.INSTANCE.between("Zz", "a0"));
        for (int i = 0; i < 1000; i++) {
            int at = (i * 13) % keys.size();
            String prev = at == 0 ? null : keys.get(at - 1);
            keys.add(at, RocicorpFractionalIndex.INSTANCE.between(prev, keys.get(at)));
        }
        for (int i = 1; i < keys.size(); i++) {
            assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0,
                    "位置 " + i + " 顺序破坏: " + keys.get(i - 1) + " / " + keys.get(i));
            assertTrue(RocicorpFractionalIndex.INSTANCE.isValid(keys.get(i)), "非法键: " + keys.get(i));
        }
    }

    /** 最小合法纯整数键 "A0…01" 的直接前驱不存在：必须抛异常而非返回非法键。 */
    @Test
    void betweenNullAndSmallestLegalPureIntegerThrows() {
        String smallestPure = "A" + "0".repeat(25) + "1"; // 最小合法整数，无小数
        assertTrue(RocicorpFractionalIndex.INSTANCE.isValid(smallestPure));
        assertThrows(IllegalStateException.class,
                () -> RocicorpFractionalIndex.INSTANCE.between(null, smallestPure));
    }

    /** 次小合法纯整数键的前插应正常返回最小合法整数（验证地板之上仍可用）。 */
    @Test
    void betweenNullAndSecondSmallestPureIntegerReturnsSmallestLegal() {
        String secondSmallest = "A" + "0".repeat(25) + "2";
        String expected = "A" + "0".repeat(25) + "1";
        String c = RocicorpFractionalIndex.INSTANCE.between(null, secondSmallest);
        assertEquals(expected, c);
        assertTrue(RocicorpFractionalIndex.INSTANCE.isValid(c));
        assertTrue(RocicorpFractionalIndex.INSTANCE.compare(c, secondSmallest) < 0);
    }

    /** 大量连续前插（走递减整数部分路径）全程必须保持合法且严格递减，不触碰非法地板。 */
    @Test
    void heavyPrependStaysValidAndStrictlyDecreasing() {
        RocicorpFractionalIndex f = RocicorpFractionalIndex.INSTANCE;
        String head = f.first();
        for (int i = 0; i < 20000; i++) {
            String c = f.between(null, head);
            assertTrue(f.isValid(c), "前插生成非法键: " + c);
            assertTrue(f.compare(c, head) < 0, c + " 应小于 " + head);
            head = c;
        }
    }

    // ====== 模型化随机压测：插入 / 拖动 / 删除，出错即打印完整上下文 ======

    /**
     * 模型化随机压测：构造一个列表，执行大量随机的插入 / 拖动 / 删除，每一步后立即校验
     * （严格全序、键合法、无重复）。一旦某步不正确，异常信息会携带随机种子、步号、操作类型与参数、
     * 生成的键、相邻上下文以及近期操作历史，便于精确定位是哪一步、哪个键出了问题。
     * 用固定种子保证可复现：失败后可据此重跑定位。
     */
    @Test
    void modelBasedRandomInsertMoveDelete() {
        // 默认每次运行使用随机种子以覆盖更多场景；
        // 若需复现失败现场，可用 -DfractionalIndex.stress.seed=<long> 固定同一种子重跑。
        String seedProp = System.getProperty("fractionalIndex.stress.seed");
        final long seed = seedProp != null ? Long.parseLong(seedProp) : new Random().nextLong();
        System.out.println("[RocicorpFractionalIndexStress] 本次随机种子 seed=" + seed);
        final int steps = 4000;
        final RocicorpFractionalIndex f = RocicorpFractionalIndex.INSTANCE;
        Random rnd = new Random(seed);

        List<String> keys = new ArrayList<>();      // 按 compareTo 升序，即被测试的“真实”列表
        keys.add(f.first());
        List<String> recent = new ArrayList<>();     // 近期操作历史（滚动保留，用于回溯）

        for (int step = 1; step <= steps; step++) {
            int op = rnd.nextInt(3); // 0=插入 1=拖动 2=删除
            if (op == 0 || keys.size() < 2) {
                // 插入：在随机位置 p 生成 between(prev, next) 并放入
                int p = rnd.nextInt(keys.size() + 1);
                String prev = p == 0 ? null : keys.get(p - 1);
                String next = p == keys.size() ? null : keys.get(p);
                String newKey = f.between(prev, next);
                assertInsertOK(f, keys, p, prev, next, newKey, step, seed, recent, "INSERT");
                keys.add(p, newKey);
                logOp(recent, step, "INSERT @" + p + " prev=" + prev + " next=" + next + " -> " + newKey);
            } else if (op == 1) {
                // 拖动：把 from 处元素移到 to 处（先移除再按新邻居重新生成键）
                int from = rnd.nextInt(keys.size());
                String moved = keys.remove(from);
                int to = rnd.nextInt(keys.size() + 1);
                String prev = to == 0 ? null : keys.get(to - 1);
                String next = to == keys.size() ? null : keys.get(to);
                String newKey = f.between(prev, next);
                assertInsertOK(f, keys, to, prev, next, newKey, step, seed, recent,
                        "MOVE(" + moved + " from=" + from + " to=" + to + ")");
                keys.add(to, newKey);
                logOp(recent, step, "MOVE " + moved + " from=" + from + " to=" + to
                        + " prev=" + prev + " next=" + next + " -> " + newKey);
            } else {
                // 删除
                int at = rnd.nextInt(keys.size());
                String removed = keys.remove(at);
                logOp(recent, step, "DELETE @" + at + " " + removed);
            }
            // 每步后做整体校验（全序 + 合法 + 唯一）
            verifyGlobal(f, keys, step, seed, recent);
        }
    }

    /** 单步插入的局部断言：新键必须合法且严格落在 (prev, next) 之间。失败时携带完整上下文。 */
    static void assertInsertOK(RocicorpFractionalIndex f, List<String> keys, int pos,
            String prev, String next, String newKey, int step, long seed, List<String> recent, String op) {
        if (newKey == null) {
            fail(context("生成的键为 null", op, step, seed, recent, prev, next, null, keys, pos));
        }
        if (!f.isValid(newKey)) {
            fail(context("生成的键非法（isValid=false）", op, step, seed, recent, prev, next, newKey, keys, pos));
        }
        if (prev != null && f.compare(prev, newKey) >= 0) {
            fail(context("新键未大于前驱", op, step, seed, recent, prev, next, newKey, keys, pos));
        }
        if (next != null && f.compare(newKey, next) >= 0) {
            fail(context("新键未小于后继", op, step, seed, recent, prev, next, newKey, keys, pos));
        }
    }

    /** 列表整体校验：全序、每个键合法、无重复。失败时携带出错点附近窗口与操作历史。 */
    static void verifyGlobal(RocicorpFractionalIndex f, List<String> keys, int step, long seed, List<String> recent) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            if (!f.isValid(k)) {
                fail(globalContext("列表中键非法", step, seed, recent, keys, i));
            }
            if (!seen.add(k)) {
                fail(globalContext("出现重复键", step, seed, recent, keys, i));
            }
            if (i > 0 && f.compare(keys.get(i - 1), k) >= 0) {
                fail(globalContext("全序被破坏（相邻键未严格递增）", step, seed, recent, keys, i));
            }
        }
    }

    static String context(String problem, String op, int step, long seed,
            List<String> recent, String prev, String next, String newKey, List<String> keys, int pos) {
        StringBuilder sb = new StringBuilder();
        sb.append("【步骤 ").append(step).append(" seed=").append(seed).append("】").append(op)
          .append(" 失败：").append(problem).append('\n');
        sb.append("  prev  = ").append(prev).append('\n');
        sb.append("  next  = ").append(next).append('\n');
        sb.append("  newKey= ").append(newKey).append('\n');
        sb.append("  插入位置 pos=").append(pos).append("，列表当前大小=").append(keys.size()).append('\n');
        sb.append("—— 近期操作历史（用于回溯） ——\n").append(recentHistory(recent));
        return sb.toString();
    }

    static String globalContext(String problem, int step, long seed,
            List<String> recent, List<String> keys, int violationIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("【步骤 ").append(step).append(" seed=").append(seed).append("】全局校验失败：")
          .append(problem).append(" @index=").append(violationIndex).append('\n');
        sb.append("  列表大小=").append(keys.size()).append('\n');
        int from = Math.max(0, violationIndex - 8);
        int to = Math.min(keys.size(), violationIndex + 9);
        sb.append("—— 出错点附近窗口（index: key） ——\n");
        for (int i = from; i < to; i++) {
            sb.append("  ").append(i).append(": ").append(keys.get(i));
            if (i == violationIndex) sb.append("   <-- 此处");
            sb.append('\n');
        }
        sb.append("—— 近期操作历史 ——\n").append(recentHistory(recent));
        return sb.toString();
    }

    static String recentHistory(List<String> recent) {
        StringBuilder sb = new StringBuilder();
        for (String s : recent) {
            sb.append("  ").append(s).append('\n');
        }
        return sb.toString();
    }

    static void logOp(List<String> recent, int step, String s) {
        recent.add("#" + step + " " + s);
        if (recent.size() > 30) recent.remove(0);
    }

    /**
     * 回归：纯整数键与其「同整数 + 单位小数」邻居之间取中点时，不得因空小数部分而崩溃。
     * 该组合会触发 midpoint 最后分支对空串执行 substring(1)。
     */
    @Test
    void betweenPureIntegerAndSingleDigitFractionStaysValid() {
        RocicorpFractionalIndex f = RocicorpFractionalIndex.INSTANCE;
        for (String[] pair : new String[][]{
                {"Zz", "Zz1"}, {"a0", "a01"}, {"Zz", "Zz0V"}, {"a0", "a00V"}
        }) {
            String lo = pair[0], hi = pair[1];
            assertTrue(f.compare(lo, hi) < 0, lo + " 应小于 " + hi);
            String c = f.between(lo, hi);
            assertTrue(f.isValid(c), "between(" + lo + "," + hi + ") 生成非法键: " + c);
            assertTrue(f.compare(lo, c) < 0 && f.compare(c, hi) < 0,
                    c + " 应落在 " + lo + " / " + hi + " 之间");
        }
        // 反向前插同理
        String c = f.between(null, "Zz1");
        assertTrue(f.isValid(c));
        assertTrue(f.compare(c, "Zz1") < 0);
    }
}
