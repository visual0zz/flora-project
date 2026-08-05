package com.flora.syntax.peg.impl;

/** 字符类解析工具：从字符类内文解析区间表 {@code [lo,hi]}，支持转义、范围、字面连字符。 */
final class CharClass {

    private CharClass() {}

    static int[][] parseRanges(String inner) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int i = 0;
        while (i < inner.length()) {
            int base;
            if (inner.charAt(i) == '\\') {
                base = Esc.decode(inner, i);
                i += Esc.escapeLength(inner, i);
            } else {
                base = inner.charAt(i);
                i++;
            }
            // 范围：base '-' end（- 后仍有内容）
            if (i + 1 < inner.length() && inner.charAt(i) == '-') {
                int j = i + 1;
                int end;
                if (inner.charAt(j) == '\\') {
                    end = Esc.decode(inner, j);
                    i = j + Esc.escapeLength(inner, j);
                } else {
                    end = inner.charAt(j);
                    i = j + 1;
                }
                out.add(new int[]{Math.min(base, end), Math.max(base, end)});
            } else {
                out.add(new int[]{base, base});
            }
        }
        return out.toArray(new int[0][]);
    }

    static boolean inRanges(int[][] ranges, char c) {
        for (int[] r : ranges) {
            if (c >= r[0] && c <= r[1]) return true;
        }
        return false;
    }
}
