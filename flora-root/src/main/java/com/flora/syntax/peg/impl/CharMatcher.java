package com.flora.syntax.peg.impl;

/**
 * 字符级匹配器：在给定位置尝试匹配，返回匹配长度；失败返回 -1。用于词法规则的编译结果。
 */
interface CharMatcher {

    int match(CharSequence s, int pos);

    /** 字面量文本匹配。 */
    final class Lit implements CharMatcher {
        private final String text;
        private final boolean ci;
        Lit(String text, boolean caseInsensitive) {
            this.text = text;
            this.ci = caseInsensitive;
        }
        @Override
        public int match(CharSequence s, int pos) {
            if (pos + text.length() > s.length()) return -1;
            for (int i = 0; i < text.length(); i++) {
                char a = text.charAt(i);
                char b = s.charAt(pos + i);
                if (a != b && (!ci || Character.toLowerCase(a) != Character.toLowerCase(b))) return -1;
            }
            return text.length();
        }
    }

    /** 单字符类（可取反）。 */
    final class Class implements CharMatcher {
        private final int[][] ranges; // [lo,hi]
        private final boolean negated;
        private final boolean ci;
        Class(int[][] ranges, boolean negated, boolean caseInsensitive) {
            this.ranges = ranges;
            this.negated = negated;
            this.ci = caseInsensitive;
        }
        @Override
        public int match(CharSequence s, int pos) {
            if (pos >= s.length()) return -1;
            char c = s.charAt(pos);
            boolean in = in(c);
            if (ci && !in) {
                in = in(Character.toLowerCase(c)) || in(Character.toUpperCase(c));
            }
            boolean ok = negated ? !in : in;
            return ok ? 1 : -1;
        }
        private boolean in(char c) {
            for (int[] r : ranges) {
                if (c >= r[0] && c <= r[1]) return true;
            }
            return false;
        }
    }

    /** 任意单字符。 */
    final class Any implements CharMatcher {
        @Override
        public int match(CharSequence s, int pos) {
            return pos < s.length() ? 1 : -1;
        }
    }

    /** 顺序组合。 */
    final class Seq implements CharMatcher {
        private final CharMatcher[] parts;
        Seq(CharMatcher[] parts) { this.parts = parts; }
        @Override
        public int match(CharSequence s, int pos) {
            int p = pos;
            for (CharMatcher m : parts) {
                int len = m.match(s, p);
                if (len < 0) return -1;
                p += len;
            }
            return p - pos;
        }
    }

    /** 有序选择（首个命中）。 */
    final class Choice implements CharMatcher {
        private final CharMatcher[] parts;
        Choice(CharMatcher[] parts) { this.parts = parts; }
        @Override
        public int match(CharSequence s, int pos) {
            for (CharMatcher m : parts) {
                int len = m.match(s, pos);
                if (len >= 0) return len;
            }
            return -1;
        }
    }

    /** 贪婪重复。max == -1 表示无上限。 */
    final class Repeat implements CharMatcher {
        private final CharMatcher inner;
        private final int min;
        private final int max;
        Repeat(CharMatcher inner, int min, int max) {
            this.inner = inner;
            this.min = min;
            this.max = max;
        }
        @Override
        public int match(CharSequence s, int pos) {
            int p = pos;
            int count = 0;
            while (true) {
                if (count == max) break;
                int len = inner.match(s, p);
                if (len <= 0) break; // 防零宽死循环
                p += len;
                count++;
            }
            if (count < min) return -1;
            return p - pos;
        }
    }

    /** 引用其它词法/fragment 规则。 */
    final class Ref implements CharMatcher {
        private final CharMatcher target;
        Ref(CharMatcher target) { this.target = target; }
        @Override
        public int match(CharSequence s, int pos) {
            return target.match(s, pos);
        }
    }

    /** 前瞻：内部匹配则成功且不消费。 */
    final class And implements CharMatcher {
        private final CharMatcher inner;
        And(CharMatcher inner) { this.inner = inner; }
        @Override
        public int match(CharSequence s, int pos) {
            return inner.match(s, pos) >= 0 ? 0 : -1;
        }
    }

    /** 负前瞻：内部不匹配则成功且不消费。 */
    final class Not implements CharMatcher {
        private final CharMatcher inner;
        Not(CharMatcher inner) { this.inner = inner; }
        @Override
        public int match(CharSequence s, int pos) {
            return inner.match(s, pos) < 0 ? 0 : -1;
        }
    }

    /** 从字符类内文解析出区间表 {@code [lo,hi]}。支持转义、范围、字面连字符。 */
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
            // 范围：base '-' end（- 后仍有内容且非开头）
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
}
