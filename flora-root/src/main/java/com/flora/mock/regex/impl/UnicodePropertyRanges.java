package com.flora.mock.regex.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.random.RandomGenerator;

/**
 * Unicode 属性码点区间池。
 * <p>静态扫描 BMP（0x0000..0xFFFF），把匹配各属性的码点合并为连续区间，
 * 生成时按区间长度加权直接取样，无拒绝采样、无失败路径。</p>
 * <p>属性判定与 {@code java.util.regex.Pattern} 的 {@code \p{...}} 语义一致
 * （均基于 {@link Character#getType(int)} 的 Unicode 类别）。</p>
 */
public final class UnicodePropertyRanges {

    private static final int PROP_LETTER = 0;
    private static final int PROP_LOWER = 1;
    private static final int PROP_UPPER = 2;
    private static final int PROP_NUMBER = 3;
    private static final int PROP_DIGIT = 4;
    private static final int PROP_PUNCT = 5;
    private static final int PROP_SEPARATOR = 6;
    private static final int PROPERTY_COUNT = 7;

    private UnicodePropertyRanges() {
    }

    static int propertyOf(String name) {
        return switch (name) {
            case "L" -> PROP_LETTER;
            case "Ll" -> PROP_LOWER;
            case "Lu" -> PROP_UPPER;
            case "N" -> PROP_NUMBER;
            case "Nd" -> PROP_DIGIT;
            case "P" -> PROP_PUNCT;
            case "Z" -> PROP_SEPARATOR;
            default -> -1;
        };
    }

    /**
     * 按属性名返回正向码点区间数组（扁平 {start,end,...}，含端点）；未知属性返回 null。
     * 供自动机引擎构建字符集。
     */
    public static int[] rangesOf(String name) {
        int property = propertyOf(name);
        if (property < 0) {
            return null;
        }
        return PROPERTY_RANGES[property];
    }

    /** 每个属性的正向码点区间池（偶数下标=start，奇数下标=end，含端点）。 */
    private static final int[][] PROPERTY_RANGES = buildPropertyRanges();
    /** 每个属性的取反码点区间池（BMP 内不匹配该属性的码点）。 */
    private static final int[][] NEGATED_RANGES = buildNegatedRanges();

    /** 静态扫描 BMP，把匹配各属性的码点合并为连续区间。 */
    private static int[][] buildPropertyRanges() {
        int[][] result = new int[PROPERTY_COUNT][];
        for (int p = 0; p < PROPERTY_COUNT; p++) {
            final int property = p;
            result[p] = collectRanges(cp -> matchesProperty(cp, property));
        }
        return result;
    }

    /** 取反区间 = BMP 全集（排除代理区）减去各属性区间。 */
    private static int[][] buildNegatedRanges() {
        int[][] result = new int[PROPERTY_COUNT][];
        int[] fullBmp = collectRanges(cp -> !isSurrogate(cp));
        for (int p = 0; p < PROPERTY_COUNT; p++) {
            result[p] = subtractRanges(fullBmp, PROPERTY_RANGES[p]);
        }
        return result;
    }

    /** 把满足谓词的码点合并为 [start, end] 区间数组。 */
    private static int[] collectRanges(IntPredicate predicate) {
        List<int[]> ranges = new ArrayList<>();
        int start = -1;
        for (int cp = 0; cp <= 0xFFFF; cp++) {
            if (predicate.test(cp)) {
                if (start < 0) {
                    start = cp;
                }
            } else if (start >= 0) {
                ranges.add(new int[]{start, cp - 1});
                start = -1;
            }
        }
        if (start >= 0) {
            ranges.add(new int[]{start, 0xFFFF});
        }
        int[] flat = new int[ranges.size() * 2];
        for (int i = 0; i < ranges.size(); i++) {
            flat[i * 2] = ranges.get(i)[0];
            flat[i * 2 + 1] = ranges.get(i)[1];
        }
        return flat;
    }

    /** 从 A 区间中减去 B 区间，返回 A\B 的区间数组。 */
    private static int[] subtractRanges(int[] a, int[] b) {
        List<int[]> result = new ArrayList<>();
        int bi = 0;
        for (int i = 0; i < a.length; i += 2) {
            int aStart = a[i];
            int aEnd = a[i + 1];
            int cursor = aStart;
            // 推进 b 到与当前 a 区间可能相交的位置
            while (bi < b.length && b[bi + 1] < cursor) {
                bi += 2;
            }
            int j = bi;
            while (j < b.length && b[j] <= aEnd) {
                int bStart = b[j];
                int bEnd = b[j + 1];
                if (bStart > cursor) {
                    result.add(new int[]{cursor, Math.min(bStart - 1, aEnd)});
                }
                cursor = Math.max(cursor, bEnd + 1);
                if (cursor > aEnd) {
                    break;
                }
                j += 2;
            }
            if (cursor <= aEnd) {
                result.add(new int[]{cursor, aEnd});
            }
        }
        int[] flat = new int[result.size() * 2];
        for (int i = 0; i < result.size(); i++) {
            flat[i * 2] = result.get(i)[0];
            flat[i * 2 + 1] = result.get(i)[1];
        }
        return flat;
    }

    private static boolean isSurrogate(int cp) {
        return cp >= 0xD800 && cp <= 0xDFFF;
    }

    /**
     * 从指定属性的区间池中按区间长度加权取样一个码点。
     *
     * @param property 属性 id（见 {@link #propertyOf}）
     * @param negate   true 取补集（{@code \P{...}} 语义）
     * @param random   随机源
     * @return BMP 内匹配该属性（或取反后不匹配）的码点
     */
    static int sample(int property, boolean negate, RandomGenerator random) {
        int[] ranges = negate ? NEGATED_RANGES[property] : PROPERTY_RANGES[property];
        long total = 0;
        for (int i = 0; i < ranges.length; i += 2) {
            total += (long) ranges[i + 1] - ranges[i] + 1;
        }
        if (total <= 0) {
            throw new IllegalArgumentException("空的 Unicode 属性区间池");
        }
        long pick = (long) (random.nextDouble() * total);
        for (int i = 0; i < ranges.length; i += 2) {
            long len = (long) ranges[i + 1] - ranges[i] + 1;
            if (pick < len) {
                return ranges[i] + (int) pick;
            }
            pick -= len;
        }
        return ranges[ranges.length - 2]; // 兜底（不可达）
    }

    private static boolean matchesProperty(int cp, int property) {
        int type = Character.getType(cp);
        return switch (property) {
            case PROP_LETTER -> type == Character.UPPERCASE_LETTER
                    || type == Character.LOWERCASE_LETTER
                    || type == Character.TITLECASE_LETTER
                    || type == Character.MODIFIER_LETTER
                    || type == Character.OTHER_LETTER;
            case PROP_LOWER -> type == Character.LOWERCASE_LETTER;
            case PROP_UPPER -> type == Character.UPPERCASE_LETTER;
            case PROP_NUMBER -> type == Character.LETTER_NUMBER
                    || type == Character.OTHER_NUMBER
                    || type == Character.DECIMAL_DIGIT_NUMBER;
            case PROP_DIGIT -> type == Character.DECIMAL_DIGIT_NUMBER;
            case PROP_PUNCT -> isPunctuation(cp);
            case PROP_SEPARATOR -> isSeparator(cp);
            default -> false;
        };
    }

    private static boolean isPunctuation(int cp) {
        return switch (Character.getType(cp)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    private static boolean isSeparator(int cp) {
        return switch (Character.getType(cp)) {
            case Character.SPACE_SEPARATOR,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }
}
