package com.flora.codegen.engine.runtime;

import com.flora.codegen.LazyArg;
import com.flora.codegen.TemplateFunction;
import com.flora.codegen.engine.CodeGenException;
import com.flora.codegen.engine.parser.LsonNumber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * 所有内置函数，以枚举常量统一管理。
 */
enum BuiltinFunc {

    // ═══════════════════ 比较与逻辑 ═══════════════════

    GREATER_THAN(fn("greaterThan", 2, args ->
            toDouble(args.get(0).eval()) > toDouble(args.get(1).eval()))),
    LESS_THAN(fn("lessThan", 2, args ->
            toDouble(args.get(0).eval()) < toDouble(args.get(1).eval()))),
    GREATER_THAN_OR_EQUALS(fn("greaterThanOrEquals", 2, args ->
            toDouble(args.get(0).eval()) >= toDouble(args.get(1).eval()))),
    LESS_THAN_OR_EQUALS(fn("lessThanOrEquals", 2, args ->
            toDouble(args.get(0).eval()) <= toDouble(args.get(1).eval()))),
    EQUALS(fn("equals", 2, BuiltinFunc::equalsImpl)),
    NOT_EQUALS(fn("notEquals", 2, args ->
            !Boolean.TRUE.equals(equalsImpl(args)))),
    AND(fn("and", -1, args -> {
        for (LazyArg a : args) {
            if (!Boolean.TRUE.equals(a.eval())) return Boolean.FALSE;
        }
        return Boolean.TRUE;
    })),
    OR(fn("or", -1, args -> {
        for (LazyArg a : args) {
            if (Boolean.TRUE.equals(a.eval())) return Boolean.TRUE;
        }
        return Boolean.FALSE;
    })),
    NOT(fn("not", 1, args ->
            !Boolean.TRUE.equals(args.get(0).eval()))),
    EQUALS_ANY(fn("equalsAny", -1, args -> {
        if (args.isEmpty()) return Boolean.FALSE;
        Object target = args.get(0).eval();
        for (int i = 1; i < args.size(); i++) {
            Object c = args.get(i).eval();
            if (target == null && c == null) return Boolean.TRUE;
            if (target == null || c == null) continue;
            if (target instanceof Boolean && c instanceof Boolean) {
                if (target.equals(c)) return Boolean.TRUE;
            } else if (target instanceof Number && c instanceof Number) {
                if (((Number) target).doubleValue() == ((Number) c).doubleValue()) return Boolean.TRUE;
            } else if (target.equals(c)) return Boolean.TRUE;
        }
        return Boolean.FALSE;
    })),

    // ═══════════════════ 字符串 ═══════════════════

    CAPITALIZE(fn("capitalize", 1, args -> {
        String s = requireStr("capitalize", args.get(0).eval());
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    })),
    LOWERCASE(fn("lowercase", 1, args ->
            requireStr("lowercase", args.get(0).eval()).toLowerCase())),
    UPPERCASE(fn("uppercase", 1, args ->
            requireStr("uppercase", args.get(0).eval()).toUpperCase())),
    JAVA_STRING(fn("javaString", 1, args -> {
        String s = requireStr("javaString", args.get(0).eval());
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    })),
    CONCAT(fn("concat", -1, args -> {
        StringBuilder sb = new StringBuilder();
        for (LazyArg a : args) {
            sb.append(requireStr("concat", a.eval()));
        }
        return sb.toString();
    })),
    CONTAINS(fn("contains", 2, args ->
            requireStr("contains", args.get(0).eval()).contains(requireStr("contains", args.get(1).eval())))),
    REPLACE(fn("replace", 3, args ->
            requireStr("replace", args.get(0).eval()).replace(
                    requireStr("replace", args.get(1).eval()), requireStr("replace", args.get(2).eval())))),
    STARTS_WITH(fn("startsWith", 2, args ->
            requireStr("startsWith", args.get(0).eval()).startsWith(requireStr("startsWith", args.get(1).eval())))),
    REPEAT(fn("repeat", 2, args ->
            requireStr("repeat", args.get(0).eval()).repeat(((Number) args.get(1).eval()).intValue()))),
    JOIN(fn("join", -1, BuiltinFunc::joinImpl)),

    // ═══════════════════ 判空 ═══════════════════

    NOT_NULL(fn("notNull", 1, args ->
            !Boolean.TRUE.equals(isNullImpl(args)))),
    IS_NULL(fn("isNull", 1, BuiltinFunc::isNullImpl)),
    IS_EMPTY(fn("isEmpty", 1, args -> switch (args.get(0).eval()) {
        case null -> true;
        case Collection<?> c -> c.isEmpty();
        case String s -> s.isEmpty();
        case Map<?, ?> m -> m.isEmpty();
        default -> false;
    })),
    IS_BLANK(fn("isBlank", 1, args -> {
        Object v = args.get(0).eval();
        return v == null || v.toString().isBlank();
    })),

    // ═══════════════════ 算术 ═══════════════════

    PLUS(fn("plus", 2, args -> {
        Object a = args.get(0).eval(), b = args.get(1).eval();
        if (a instanceof String || b instanceof String) {
            return requireStr("plus", a) + requireStr("plus", b);
        }
        if (isIntegerValue(a) && isIntegerValue(b)) {
            return LsonNumber.of(toLong(a) + toLong(b));
        }
        return LsonNumber.of(toDouble(a) + toDouble(b));
    })),
    MINUS(fn("minus", 2, args -> {
        Object a = args.get(0).eval(), b = args.get(1).eval();
        if (isIntegerValue(a) && isIntegerValue(b)) {
            return LsonNumber.of(toLong(a) - toLong(b));
        }
        return LsonNumber.of(toDouble(a) - toDouble(b));
    })),

    // ═══════════════════ 范围与序列 ═══════════════════

    RANGE(fn("range", 2, args -> {
        long start = toLong(args.get(0).eval()), end = toLong(args.get(1).eval());
        List<Long> result = new ArrayList<>();
        for (long i = start; i <= end; i++) result.add(i);
        return result;
    })),
    SEQ_JOIN(fn("sequenceJoin", 4, args -> {
        String tpl = requireStr("sequenceJoin", args.get(0).eval());
        long start = toLong(args.get(1).eval()), end = toLong(args.get(2).eval());
        String sep = requireStr("sequenceJoin", args.get(3).eval());
        StringBuilder sb = new StringBuilder();
        for (long i = start; i <= end; i++) {
            if (!sb.isEmpty()) sb.append(sep);
            sb.append(tpl.replace("{}", String.valueOf(i)));
        }
        return sb.toString();
    })),

    // ═══════════════════ 工具 ═══════════════════

    FIRST_NON_NULL(fn("firstNonNull", -1, args -> {
        for (LazyArg a : args) {
            Object v = a.eval();
            if (v != null) return v;
        }
        return null;
    })),
    NOW(fn("now", 1, args -> {
        String pattern = requireStr("now", args.get(0).eval());
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern(pattern));
    })),
    JAVA_PACKAGE_TO_PATH(fn("javaPackageToPath", 1, args ->
            requireStr("javaPackageToPath", args.get(0).eval()).replace('.', '/'))),
    NUMBER_FORMAT(fn("numberFormat", 2, BuiltinFunc::numberFormatImpl)),
    LENGTH(fn("length", 1, args -> {
        Object v = args.get(0).eval();
        if (v == null) return 0;
        if (v instanceof String s) return s.length();
        if (v instanceof Collection<?> c) return c.size();
        if (v instanceof Map<?, ?> m) return m.size();
        if (v.getClass().isArray()) return java.lang.reflect.Array.getLength(v);
        throw new IllegalArgumentException(
                "length 不支持类型: " + v.getClass().getSimpleName());
    })),

    // ═══════════════════ 组合生成器 ═══════════════════

    SELF_CARTESIAN(fn("selfCartesian", 2, BuiltinFunc::selfCrossProductImpl)),
    PERMUTATION(fn("permutation", 2, BuiltinFunc::fullPermutationImpl)),
    COMBINATION(fn("combination", 2, BuiltinFunc::fullCombinationImpl)),
    MULTI_COMBINATION(fn("multiCombination", 2, BuiltinFunc::multiCombinationImpl)),
    CARTESIAN(fn("cartesian", -1, BuiltinFunc::cartesianProductImpl)),
    CONCAT_LIST(fn("concatList", -1, BuiltinFunc::concatListImpl)),
    CONCAT_FIELD(fn("concatField", 2, BuiltinFunc::concatFieldImpl)),
    SORT_BY(fn("sortBy", 2, BuiltinFunc::sortByImpl)),

    // ═══════════════════ 序列分析 ═══════════════════

    /**
     * 统计列表中截至指定索引（不含）时，某字段等于指定值的元素个数。
     * <p>参数：list, fieldName, value, beforeIndex（不包含的结束索引，1=只检查第0个）</p>
     */
    PREFIX_COUNT(fn("prefixCount", 4, BuiltinFunc::prefixCountImpl)),

    /**
     * 统计列表中从指定索引之后（不含）起，某字段等于指定值的元素个数。
     * <p>参数：list, fieldName, value, afterIndex（不包含的起始索引，1=从第0个之后开始）</p>
     */
    SUFFIX_COUNT(fn("suffixCount", 4, BuiltinFunc::suffixCountImpl));

    // ═══════════════════ 字段与访问方法 ═══════════════════

    private final TemplateFunction func;

    BuiltinFunc(TemplateFunction func) {
        this.func = func;
    }

    /** 返回该常量的 {@link TemplateFunction} 实例。 */
    TemplateFunction asFunc() {
        return func;
    }

    // ═══════════════════ 共享辅助方法 ═══════════════════

    /** 求值所有惰性参数，返回实值列表。 */
    private static List<Object> evalAll(List<LazyArg> args) {
        return args.stream().map(LazyArg::eval).toList();
    }

    private static Object equalsImpl(List<LazyArg> args) {
        Object a = args.get(0).eval(), b = args.get(1).eval();
        if (a == null && b == null) return Boolean.TRUE;
        if (a == null || b == null) return Boolean.FALSE;
        if (a instanceof Boolean && b instanceof Boolean) return a.equals(b);
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        return java.util.Objects.equals(a, b);
    }

    private static Object isNullImpl(List<LazyArg> args) {
        return args.get(0).eval() == null;
    }

    private static Object joinImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        if (args.size() < 2) {
            throw new IllegalArgumentException("join 至少需要 2 个参数（分隔符 + 至少一个元素）");
        }
        String sep = str(args.get(0));
        StringJoiner sj = new StringJoiner(sep);
        for (int i = 1; i < args.size(); i++) {
            sj.add(requireStr("join", args.get(i)));
        }
        return sj.toString();
    }

    // ---- 组合生成器实现 ----

    @SuppressWarnings("unchecked")
    private static List<List<Object>> selfCrossProductImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<?> items = castItems(args.get(0));
        int k = ((Number) args.get(1)).intValue();
        List<List<Object>> results = new ArrayList<>();
        buildCrossProduct(items, k, 0, new ArrayList<>(), results);
        return results;
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> fullPermutationImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<?> items = castItems(args.get(0));
        int k = ((Number) args.get(1)).intValue();
        List<List<Object>> results = new ArrayList<>();
        buildPermutations(items, k, 0, new ArrayList<>(), new HashSet<>(), results);
        return results;
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> fullCombinationImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<?> items = castItems(args.get(0));
        int k = ((Number) args.get(1)).intValue();
        List<List<Object>> results = new ArrayList<>();
        buildCombinations(items, k, 0, new ArrayList<>(), new HashSet<>(), results);
        return results;
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> multiCombinationImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<?> items = castItems(args.get(0));
        int k = ((Number) args.get(1)).intValue();
        List<List<Object>> results = new ArrayList<>();
        buildMultiCombination(items, k, 0, new ArrayList<>(), results);
        return results;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> concatListImpl(List<LazyArg> lazyArgs) {
        List<Object> result = new ArrayList<>();
        for (LazyArg la : lazyArgs) {
            Object arg = la.eval();
            if (arg instanceof List<?> list) {
                result.addAll(list);
            } else {
                throw new IllegalArgumentException("concatList 的每个参数必须是列表");
            }
        }
        return result;
    }

    private static Object concatFieldImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<?> list = (List<?>) args.get(0);
        String field = args.get(1).toString();
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object v = map.get(field);
                if (v != null) sb.append(v);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Object sortByImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<Object> list = (List<Object>) args.get(0);
        String field = args.get(1).toString();
        List<Object> result = new ArrayList<>(list);
        result.sort((a, b) -> {
            Object va = (a instanceof Map<?, ?> ma) ? ma.get(field) : null;
            Object vb = (b instanceof Map<?, ?> mb) ? mb.get(field) : null;
            if (va == null && vb == null) return 0;
            if (va == null) return -1;
            if (vb == null) return 1;
            return ((Comparable<Object>) va).compareTo(vb);
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<?> castItems(Object v) {
        if (v instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException("组合生成器的第一个参数必须是列表");
    }

    private static void buildCrossProduct(List<?> items, int k, int depth,
                                           List<Object> current,
                                           List<List<Object>> results) {
        if (depth == k) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (Object item : items) {
            current.add(item);
            buildCrossProduct(items, k, depth + 1, current, results);
            current.remove(current.size() - 1);
        }
    }

    private static void buildPermutations(List<?> items, int k, int depth,
                                           List<Object> current,
                                           Set<Object> used,
                                           List<List<Object>> results) {
        if (depth == k) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (Object item : items) {
            if (!used.add(item)) continue;
            current.add(item);
            buildPermutations(items, k, depth + 1, current, used, results);
            current.remove(current.size() - 1);
            used.remove(item);
        }
    }

    private static void buildCombinations(List<?> items, int k, int start,
                                           List<Object> current,
                                           Set<Object> used,
                                           List<List<Object>> results) {
        if (k == 0) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i <= items.size() - k; i++) {
            Object item = items.get(i);
            current.add(item);
            used.add(item);
            buildCombinations(items, k - 1, i + 1, current, used, results);
            current.remove(current.size() - 1);
            used.remove(item);
        }
    }

    private static void buildMultiCombination(List<?> items, int k, int start,
                                               List<Object> current,
                                               List<List<Object>> results) {
        if (k == 0) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            buildMultiCombination(items, k - 1, i, current, results);
            current.remove(current.size() - 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object cartesianProductImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<List<Object>> lists = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof List<?> list) {
                lists.add((List<Object>) list);
            } else {
                throw new IllegalArgumentException("cartesianProduct 的每个参数必须是列表");
            }
        }
        List<List<Object>> results = new ArrayList<>();
        buildCartesianProduct(lists, 0, new ArrayList<>(), results);
        return results;
    }

    private static void buildCartesianProduct(List<List<Object>> lists, int depth,
                                               List<Object> current,
                                               List<List<Object>> results) {
        if (depth == lists.size()) {
            results.add(new ArrayList<>(current));
            return;
        }
        for (Object item : lists.get(depth)) {
            current.add(item);
            buildCartesianProduct(lists, depth + 1, current, results);
            current.remove(current.size() - 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object prefixCountImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<Object> list = (List<Object>) args.get(0);
        String field = args.get(1).toString();
        Object value = args.get(2);
        int beforeIndex = ((Number) args.get(3)).intValue();
        int count = 0;
        int limit = Math.min(beforeIndex, list.size());
        for (int idx = 0; idx < limit; idx++) {
            Object item = list.get(idx);
            if (item instanceof Map<?, ?> map) {
                Object v = map.get(field);
                if (Objects.equals(v, value)) count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static Object suffixCountImpl(List<LazyArg> lazyArgs) {
        List<Object> args = evalAll(lazyArgs);
        List<Object> list = (List<Object>) args.get(0);
        String field = args.get(1).toString();
        Object value = args.get(2);
        int afterIndex = ((Number) args.get(3)).intValue();
        int count = 0;
        int start = afterIndex + 1;
        for (int idx = start; idx < list.size(); idx++) {
            Object item = list.get(idx);
            if (item instanceof Map<?, ?> map) {
                Object v = map.get(field);
                if (Objects.equals(v, value)) count++;
            }
        }
        return count;
    }

    private static double toDouble(Object v) {
        return LsonNumber.toDouble(v, "比较运算");
    }

    private static long toLong(Object v) {
        return LsonNumber.toLong(v, "range");
    }

    /** 将对象转为字符串，null 直接抛异常。用于字符串处理函数。 */
    private static String requireStr(String funcName, Object v) {
        if (v == null) {
            throw new CodeGenException(funcName + ": 参数不能为 null");
        }
        return v.toString();
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static Object numberFormatImpl(List<LazyArg> args) {
        Object v = args.get(0).eval();
        String fmt = requireStr("numberFormat", args.get(1).eval());
        if (v instanceof Number n) {
            return new java.text.DecimalFormat(fmt).format(n);
        }
        throw new IllegalArgumentException(
                "numberFormat 的第一个参数必须是数值，实际为: " + (v == null ? "null" : v.getClass().getSimpleName()));
    }

    private static boolean isIntegerValue(Object v) {
        if (v instanceof LsonNumber n) return !n.isDouble();
        return v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long;
    }

    /** 创建内联 TemplateFunction 实例。 */
    private static TemplateFunction fn(String name, int arity, Function<List<LazyArg>, Object> impl) {
        return new TemplateFunction() {
            @Override public String name() { return name; }
            @Override public int arity() { return arity; }
            @Override public Object apply(List<LazyArg> args) { return impl.apply(args); }
        };
    }
}
