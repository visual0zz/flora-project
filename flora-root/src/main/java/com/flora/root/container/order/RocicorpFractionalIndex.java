package com.flora.root.container.order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 小数索引（fractional indexing）：生成可排序的字符串键，使元素能在任意两个邻居之间插入，
 * 而不必重排序列——插入只改写被插入元素自身的键。
 *
 * <p><b>键的结构</b>：整数部分 + 小数部分。整数部分的首字符是"头部"（head），取自
 * {@link #INT_DIGITS}（{@code A-Za-z}），编码整数部分的长度与量级：相邻的两个字符
 * {@code Z}/{@code a} 表示最短（2 位），向两端递增至 {@code A}/{@code z}（27 位），
 * 即长度随量级增长（变长整数）。其余位（整数部分余位与全部小数部分）取自
 * {@link #DIGITS}（{@code 0-9A-Za-z}）。典型键形如 {@code a0}、{@code a1}、{@code b00}、{@code Zz}。</p>
 *
 * <p><b>排序</b>：直接按字典序（{@link String#compareTo}）——两个字符集内部均为升序、
 * 且字典序即数值序，故无需解析。禁止用大小写无关的比较（如 {@code String.CASE_INSENSITIVE_ORDER}）。</p>
 *
 * <p><b>为何前插与追加都便宜</b>：追加是整数部分加一、前插是整数部分减一，只有进位/借位
 * 传播到头部时才改变键长，且头部把"长度"也编码了进来，因此两者都不必触碰小数部分，
 * 键长增长极慢（约 62 次追加或前插才增一位）。这与"在小数部分反复取中点"的做法不同——
 * 后者每次插入都把可用间隙砍半，几次即耗尽。</p>
 *
 * <p><b>约束</b>（生成结果恒满足，{@link #isValid} 可校验外部来源的键）：
 * 小数部分不能以 {@code 0} 结尾——以 {@code 0} 结尾的键与其自身前缀之间不存在可插入区间；
 * 键不能等于最小整数 {@code A} 后接 26 个 {@code 0}，它已无法再向前生成。</p>
 *
 * <p>{@link #between} 是确定性的（相同输入必得相同输出）；{@link #betweenJittered}
 * 在取中点时改为随机取值，用于多端并发插入同一区间的场景，使两端得到不同但可比较的键。</p>
 *
 * <p>本类无状态，线程安全。</p>
 */
public final class RocicorpFractionalIndex {

    /** 数字位字符集（升序，且与 ASCII 码点序一致）。 */
    public static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** 整数段头部字符集：{@code A-Z} 为负向量级，{@code a-z} 为正向量级。 */
    public static final String INT_DIGITS = DIGITS.substring(10);

    private static final int BASE = DIGITS.length();
    private static final int INT_BASE = INT_DIGITS.length();
    private static final int HALF = INT_BASE / 2;
    private static final char ZERO = DIGITS.charAt(0);
    private static final char LAST = DIGITS.charAt(BASE - 1);

    /** 最小整数：最长负向头部 {@code A} 后接全 0，不可再减。 */
    private static final String SMALLEST_INTEGER = INT_DIGITS.charAt(0) + "0".repeat(HALF);

    private static final int[] DIGIT_INDEX = indexTable(DIGITS);
    private static final int[] INT_DIGIT_INDEX = indexTable(INT_DIGITS);

    private RocicorpFractionalIndex() {
    }

    // ====== 公开 API ======

    /** 首个键（等价于 {@code between(null, null)}），形如 {@code a0}。 */
    public static String first() {
        return between(null, null);
    }

    /**
     * 生成严格位于 {@code a} 与 {@code b} 之间的键。
     *
     * @param a 下界；null 表示插到最前
     * @param b 上界；null 表示追加到末尾
     * @throws IllegalArgumentException 两界均非 null 且 {@code a >= b}，或传入的键非法
     */
    public static String between(String a, String b) {
        return between(a, b, false);
    }

    /**
     * 同 {@link #between}，但在取中点时随机取值而非取正中。
     * <p>用于多端并发场景：两端在同一区间插入会得到不同但可比较的键，合并后按字典序
     * 即可得到一致次序。相邻（无中点可取）的分支仍为确定性。</p>
     */
    public static String betweenJittered(String a, String b) {
        return between(a, b, true);
    }

    /**
     * 生成 {@code n} 个均匀分布在 {@code a} 与 {@code b} 之间的键（升序）。
     * <p>比逐个调用 {@link #between} 得到的键更短——后者会让后面的键反复在前一个的
     * 右半区取中点，而本方法按分治均匀铺开。</p>
     *
     * @param n 需要的键数量，{@code n >= 0}
     */
    public static List<String> nBetween(String a, String b, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n 不能为负: " + n);
        }
        if (n == 0) {
            return List.of();
        }
        if (n == 1) {
            return List.of(between(a, b));
        }
        if (b == null) {
            List<String> out = new ArrayList<>(n);
            String c = between(a, null);
            out.add(c);
            for (int i = 1; i < n; i++) {
                c = between(c, null);
                out.add(c);
            }
            return List.copyOf(out);
        }
        if (a == null) {
            List<String> out = new ArrayList<>(n);
            String c = between(null, b);
            out.add(c);
            for (int i = 1; i < n; i++) {
                c = between(null, c);
                out.add(c);
            }
            Collections.reverse(out);
            return List.copyOf(out);
        }
        int mid = n / 2;
        String c = between(a, b);
        List<String> out = new ArrayList<>(n);
        out.addAll(nBetween(a, c, mid));
        out.add(c);
        out.addAll(nBetween(c, b, n - mid - 1));
        return List.copyOf(out);
    }

    /** 键是否为合法格式（非 null、头部有效、整数部分完整、小数部分不以 0 结尾）。 */
    public static boolean isValid(String key) {
        if (key == null || key.isEmpty() || SMALLEST_INTEGER.equals(key)) {
            return false;
        }
        int len;
        try {
            len = integerLength(key.charAt(0));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (len > key.length()) {
            return false;
        }
        String fraction = key.substring(len);
        return fraction.isEmpty() || fraction.charAt(fraction.length() - 1) != ZERO;
    }

    /**
     * 比较两个键的先后，可直接用于排序。
     * <p>必须按字典序比较——两个字符集内部均为升序、且字典序即数值序。切勿改用大小写无关的
     * 比较（如 {@code String.CASE_INSENSITIVE_ORDER}），那会得出错误次序。
     * {@code null}（无键）排在最前。</p>
     */
    public static int compare(String a, String b) {
        if (a == null) {
            return b == null ? 0 : -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    // ====== 生成主体 ======

    private static String between(String a, String b, boolean jittered) {
        if (a == null) {
            if (b == null) {
                // 最短正向头部 + 最小的数字位
                return INT_DIGITS.charAt(HALF) + String.valueOf(ZERO);
            }
            requireValid(b);
            String ib = integerPart(b);
            String fb = b.substring(ib.length());
            if (SMALLEST_INTEGER.equals(ib)) {
                // 已是最负整数，无法再减：改为在其小数部分与 0 之间取中点
                return ib + midpoint("", fb, jittered);
            }
            if (ib.compareTo(b) < 0) {
                return ib; // b 带小数部分，其整数部分本身即严格小于 b
            }
            String smaller = decrementInteger(ib);
            if (smaller == null) {
                throw new IllegalStateException("无法再向前生成键（已达最小整数）: " + b);
            }
            return smaller;
        }
        if (b == null) {
            requireValid(a);
            String ia = integerPart(a);
            String fa = a.substring(ia.length());
            String bigger = incrementInteger(ia);
            // 整数部分已达最大：改为在其小数部分与"无穷大"之间取中点
            return bigger == null ? ia + midpoint(fa, null, jittered) : bigger;
        }
        if (a.compareTo(b) >= 0) {
            throw new IllegalArgumentException("a 必须小于 b: " + a + " / " + b);
        }
        requireValid(a);
        requireValid(b);
        String ia = integerPart(a);
        String fa = a.substring(ia.length());
        String ib = integerPart(b);
        String fb = b.substring(ib.length());
        if (ia.equals(ib)) {
            return ia + midpoint(fa, fb, jittered);
        }
        String bigger = incrementInteger(ia);
        if (bigger == null) {
            throw new IllegalStateException("无法再向后生成键（已达最大整数）: " + a);
        }
        if (bigger.compareTo(b) < 0) {
            return bigger; // 整数部分加一后仍小于 b，直接用
        }
        return ia + midpoint(fa, null, jittered);
    }

    /**
     * 求 a 与 b 的中点（{@code a < b}；b 为 null 表示无上界）。
     * <p>先剥最长公共前缀再递归，使比较始终发生在两者首个不同的位上；该位取不到中点
     * （相邻）时，若 b 有多位则取 b 的首位（它严格落在区间内），否则固定 a 的首位并
     * 对剩余部分继续求中点。</p>
     */
    private static String midpoint(String a, String b, boolean jittered) {
        if (b != null && a.compareTo(b) >= 0) {
            throw new IllegalArgumentException(a + " >= " + b);
        }
        if (endsWithZero(a) || (b != null && endsWithZero(b))) {
            throw new IllegalArgumentException("键不能以 0 结尾: " + a + " / " + b);
        }
        if (b != null) {
            int n = 0;
            while (n < b.length() && charAtOrZero(a, n) == b.charAt(n)) {
                n++;
            }
            if (n > 0) {
                return b.substring(0, n)
                        + midpoint(a.substring(Math.min(n, a.length())), b.substring(n), jittered);
            }
        }
        int da = a.isEmpty() ? 0 : digitIndex(a.charAt(0));
        int db = b == null ? BASE : digitIndex(b.charAt(0));
        if (db - da > 1) {
            return String.valueOf(DIGITS.charAt(midDigit(da, db, jittered)));
        }
        if (b != null && b.length() > 1) {
            return b.substring(0, 1);
        }
        // b 为空或仅一位：固定 a 的首位，对剩余部分继续求中点
        return DIGITS.charAt(da) + midpoint(a.substring(1), null, jittered);
    }

    /** 取 da 与 db 之间的中点数字；{@code jittered} 时改为在开区间内随机取。 */
    private static int midDigit(int da, int db, boolean jittered) {
        if (!jittered) {
            return (da + db + 1) / 2;
        }
        return da + 1 + ThreadLocalRandom.current().nextInt(db - da - 1);
    }

    // ====== 变长整数 ======

    /**
     * 头部字符所编码的整数部分长度。
     * <p>头部字符集对半分：前半（{@code A-Z}）为负向量级、后半（{@code a-z}）为正向量级，
     * 两半的交界处（{@code Z}/{@code a}）最短（2 位），越靠两端越长（至 27 位）。</p>
     */
    private static int integerLength(char head) {
        int i = head < 256 ? INT_DIGIT_INDEX[head] : -1;
        if (i < 0) {
            throw new IllegalArgumentException("非法 order 键头部: " + head);
        }
        return i < HALF ? HALF - i + 1 : i - HALF + 2;
    }

    private static String integerPart(String key) {
        int len = integerLength(key.charAt(0));
        if (len > key.length()) {
            throw new IllegalArgumentException("非法 order 键（整数部分不完整）: " + key);
        }
        return key.substring(0, len);
    }

    /** 整数部分加一；已达最大整数时返回 null。 */
    private static String incrementInteger(String x) {
        return stepInteger(x, 1);
    }

    /** 整数部分减一；已达最小整数时返回 null。 */
    private static String decrementInteger(String x) {
        return stepInteger(x, -1);
    }

    /**
     * 整数部分按 {@code step}（±1）步进：从末位开始逐位加减，该位溢出则向前进位/借位；
     * 传播到头部时换用相邻头部字符，并按新头部所编码的长度增减位数（变长整数）。
     *
     * @return 步进结果；已是最外端整数、无法再步进时返回 null
     */
    private static String stepInteger(String x, int step) {
        validateIntegerPart(x);
        char head = x.charAt(0);
        char wrap = step > 0 ? ZERO : LAST;      // 该位进位/借位后落成的字符
        int overflow = step > 0 ? BASE : -1;     // 该位溢出/下溢的判定值
        int edge = step > 0 ? INT_BASE - 1 : 0;  // 最外端头部的下标
        StringBuilder trailing = new StringBuilder();
        for (int i = x.length() - 1; i >= 1; i--) {
            int d = digitIndex(x.charAt(i)) + step;
            if (d == overflow) {
                trailing.insert(0, wrap);
            } else {
                return head + x.substring(1, i) + DIGITS.charAt(d) + trailing;
            }
        }
        int headIndex = INT_DIGIT_INDEX[head];
        if (headIndex == edge) {
            return null;
        }
        char h = INT_DIGITS.charAt(headIndex + step);
        int delta = integerLength(h) - integerLength(head);
        String t = trailing.toString();
        if (delta > 0) {
            return h + t + wrap;
        }
        return delta < 0 ? h + t.substring(1) : h + t;
    }

    private static void validateIntegerPart(String x) {
        if (integerLength(x.charAt(0)) != x.length()) {
            throw new IllegalArgumentException("非法整数部分: " + x);
        }
    }

    private static void requireValid(String key) {
        if (!isValid(key)) {
            throw new IllegalArgumentException("非法 order 键: " + key);
        }
    }

    // ====== 字符表 ======

    private static int[] indexTable(String digits) {
        int[] table = new int[256];
        java.util.Arrays.fill(table, -1);
        for (int i = 0; i < digits.length(); i++) {
            table[digits.charAt(i)] = i;
        }
        return table;
    }

    private static int digitIndex(char c) {
        int d = c < 256 ? DIGIT_INDEX[c] : -1;
        if (d < 0) {
            throw new IllegalArgumentException("非法 order 键字符: " + c);
        }
        return d;
    }

    /** 第 i 位字符；越界按 {@code 0} 处理，等价于右侧补 0。 */
    private static char charAtOrZero(String s, int i) {
        return i < s.length() ? s.charAt(i) : ZERO;
    }

    private static boolean endsWithZero(String s) {
        return !s.isEmpty() && s.charAt(s.length() - 1) == ZERO;
    }
}
