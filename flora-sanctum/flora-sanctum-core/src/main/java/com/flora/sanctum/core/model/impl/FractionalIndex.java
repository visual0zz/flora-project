package com.flora.sanctum.core.model.impl;

/**
 * 小数索引（fractional indexing）：以 base62 字符串作排序键，使节点可在任意两个邻居之间
 * 插入而不必重排整段——被移动/插入的节点只改写自身 {@code order}。
 *
 * <p>键形如 {@code a1}：第 0 位是整数段，其余是小数段。字符集为 {@code 0-9A-Za-z}
 * （base62），字符在集中的先后即数值大小，且该先后与字符的 ASCII 码点序一致，
 * 因此排序直接用 {@link String#compareTo}：字典序即数值序，无需解析。</p>
 *
 * <p><b>精度无界</b>：两个键之间取不到中点时向下追加一位继续取，长度按需增长，故不存在
 * 间隙耗尽，也不需要整段重排。追加（{@link #after}）走小数段进位，长度增长极慢
 * （约 62 次追加才增一位）。</p>
 *
 * <p><b>约定：生成的键不以 {@code 0} 结尾。</b>以 {@code 0} 结尾的键与其自身前缀之间
 * 不存在可插入的区间（{@code a0} 与 {@code a00} 之间无解），本类所有生成路径均避开
 * 该情形，保证任意两个键之间恒有插入空间。</p>
 *
 * <p>生成为确定性的：相同输入必得相同输出，便于测试与调试。</p>
 *
 * <p>本类只做纯数学，不依赖持久化细节，便于独立单测。</p>
 */
public final class FractionalIndex {

    /** base62 字符集（顺序即数值序，且与 ASCII 码点序一致）。 */
    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int BASE = DIGITS.length();

    /** 空列表的首个 order：整数段取中段 'a'，小数段 "1"（不以 '0' 结尾）。 */
    public static final String FIRST = "a1";

    /** 相邻时向下追加的中点字符（BASE/2）。 */
    private static final char MID = DIGITS.charAt(BASE / 2);

    private FractionalIndex() {
    }

    /** 首个 order。 */
    public static String first() {
        return FIRST;
    }

    /**
     * 取紧随 {@code last} 之后的键（追加到末尾）。
     * <p>小数段末位进一并向前进位。进位会使末位落回 {@code 0}，与「不以 0 结尾」约定冲突，
     * 故凡发生进位的路径都把末位复位为 {@code 1}。小数段全满时整数段进一、小数段归零；
     * 整数段也到顶时整体扩展一位。</p>
     *
     * @param last 当前末位键；null 或空表示空列表
     */
    public static String after(String last) {
        if (last == null || last.isEmpty()) {
            return FIRST;
        }
        char[] cs = last.toCharArray();
        for (int i = cs.length - 1; i >= 1; i--) {
            int d = indexOf(cs[i]);
            if (d < BASE - 1) {
                cs[i] = DIGITS.charAt(d + 1);
                if (i < cs.length - 1) {
                    cs[cs.length - 1] = DIGITS.charAt(1);
                }
                return new String(cs);
            }
            cs[i] = DIGITS.charAt(0);
        }
        int head = indexOf(cs[0]);
        if (head < BASE - 1) {
            cs[0] = DIGITS.charAt(head + 1);
            for (int k = 1; k < cs.length - 1; k++) {
                cs[k] = DIGITS.charAt(0);
            }
            if (cs.length > 1) {
                cs[cs.length - 1] = DIGITS.charAt(1);
                return new String(cs);
            }
            return new String(cs) + DIGITS.charAt(1);
        }
        // 整数段已到顶，无法再进位：原样扩展一位
        return last + DIGITS.charAt(1);
    }

    /**
     * 取 {@code before} 与 {@code after} 之间的键。
     *
     * @param before 前驱键；null 或空表示插到最前
     * @param after  后继键；null 表示追加到末尾（等价 {@link #after}）
     */
    public static String between(String before, String after) {
        String a = before == null ? "" : before;
        if (after == null) {
            return after(a);
        }
        if (a.compareTo(after) >= 0) {
            throw new IllegalArgumentException("before 必须小于 after: " + a + " / " + after);
        }
        return midpoint(a, after);
    }

    /**
     * 求 a 与 b 的中点（a &lt; b）。
     * <p>先剥最长公共前缀再递归，使比较始终发生在两者首个不同的位上；该位取不到中点
     * （相邻）时，固定 a 的整体并向下追加一位——a 的首位已严格小于 b 的首位，
     * 故追加任何字符都仍落在区间内。</p>
     */
    private static String midpoint(String a, String b) {
        int limit = Math.max(a.length(), b.length());
        int n = 0;
        while (n < limit && digitAt(a, n) == digitAt(b, n)) {
            n++;
        }
        if (n > 0) {
            String head = b.substring(0, Math.min(n, b.length()));
            return head + midpoint(a.substring(Math.min(n, a.length())),
                    b.substring(Math.min(n, b.length())));
        }
        int da = digitAt(a, 0);
        int db = digitAt(b, 0);
        if (db - da > 1) {
            char mid = DIGITS.charAt(da + (db - da) / 2);
            return a.isEmpty() ? String.valueOf(mid) : mid + a.substring(1);
        }
        return (a.isEmpty() ? String.valueOf(DIGITS.charAt(0)) : a) + MID;
    }

    /** 第 i 位的数值；越界（字符串耗尽）按最小字符处理，等价于右侧补 '0'。 */
    private static int digitAt(String s, int i) {
        return i >= s.length() ? 0 : indexOf(s.charAt(i));
    }

    private static int indexOf(char c) {
        int d = DIGITS.indexOf(c);
        if (d < 0) {
            throw new IllegalArgumentException("非法 order 字符: " + c);
        }
        return d;
    }
}
