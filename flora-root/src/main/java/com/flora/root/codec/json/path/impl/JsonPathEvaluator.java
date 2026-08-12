package com.flora.root.codec.json.path.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RFC 9535 JSONPath 评估引擎。
 * <p>对输入值执行 {@link Selector} 列表，返回匹配节点列表。</p>
 */
public final class JsonPathEvaluator {

    private JsonPathEvaluator() {}

    /** 对单个 Selector 的路径片段列表求值。selectors 不含 ROOT（已隐含）。 */
    public static List<Object> evaluate(Object root, List<Selector> selectors) {
        List<Object> current = Collections.singletonList(root);
        for (Selector sel : selectors) {
            current = applySelector(current, sel, root);
            if (current.isEmpty()) break;
        }
        return current;
    }

    /** 对一个节点集应用一个选择器。 */
    private static List<Object> applySelector(List<Object> nodes, Selector sel, Object root) {
        // RFC 9535：Nodelist 保留所有匹配节点（含重复值）并按序排列，不做去重。
        List<Object> result = new ArrayList<>();
        for (Object node : nodes) {
            result.addAll(matchSingle(node, sel, root));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> matchSingle(Object node, Selector sel, Object root) {
        return switch (sel) {
            case NameSelector(var name) -> {
                if (node instanceof Map) {
                    Object v = ((Map<String, Object>) node).get(name);
                    yield v != null ? List.of(v) : Collections.emptyList();
                }
                yield Collections.emptyList();
            }
            case IndexSelector(var idx) -> {
                if (node instanceof List) {
                    List<Object> list = (List<Object>) node;
                    int i = idx < 0 ? list.size() + idx : idx;
                    if (i >= 0 && i < list.size()) yield List.of(list.get(i));
                }
                yield Collections.emptyList();
            }
            case MultiIndexSelector(var indices) -> {
                List<Object> r = new ArrayList<>();
                if (node instanceof List) {
                    List<Object> list = (List<Object>) node;
                    for (int idx : indices) {
                        int i = idx < 0 ? list.size() + idx : idx;
                        if (i >= 0 && i < list.size()) r.add(list.get(i));
                    }
                }
                yield r;
            }
            case SliceSelector(var start, var end, var step) -> {
                List<Object> r = new ArrayList<>();
                if (node instanceof List) {
                    List<Object> list = (List<Object>) node;
                    int sz = list.size();
                    int st = (step != null && step != 0) ? step : 1;
                    int s = resolveSliceBound(start, sz, st > 0 ? 0 : sz - 1);
                    int e = resolveSliceBound(end, sz, st > 0 ? sz : -1);
                    if (st > 0) {
                        for (int i = s; i < e && i < sz; i += st) {
                            if (i >= 0) r.add(list.get(i));
                        }
                    } else {
                        for (int i = s; i > e && i >= 0; i += st) {
                            if (i < sz) r.add(list.get(i));
                        }
                    }
                }
                yield r;
            }
            case WildcardSelector() -> {
                List<Object> r = new ArrayList<>();
                if (node instanceof Map) r.addAll(((Map<String, Object>) node).values());
                else if (node instanceof List) r.addAll((List<Object>) node);
                yield r;
            }
            case DescendantSelector(var memberName) -> {
                List<Object> r = new ArrayList<>();
                collectDescendants(node, memberName, r);
                yield r;
            }
            case FilterSelector(var expr) -> {
                List<Object> r = new ArrayList<>();
                if (node instanceof List) {
                    for (Object item : (List<Object>) node) {
                        if (evalFilter(expr, item, root)) r.add(item);
                    }
                }
                yield r;
            }
        };
    }

    /** 解析切片边界：null → 使用默认值；负数 → size + 负值。 */
    private static int resolveSliceBound(Integer bound, int size, int defaultVal) {
        if (bound == null) return defaultVal;
        return bound < 0 ? Math.max(size + bound, 0) : Math.min(bound, size);
    }

    // ===================== 递归下降 =====================

    private static void collectDescendants(Object node, String memberName, List<Object> result) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if (memberName == null) {
                // ..* : 收集当前节点的所有值 + 递归
                for (Object v : map.values()) {
                    result.add(v);
                    collectDescendants(v, null, result);
                }
            } else {
                // ..name : 检查当前节点 + 递归
                Object v = map.get(memberName);
                if (v != null) result.add(v);
                for (Object child : map.values()) {
                    if (child instanceof Map || child instanceof List) {
                        collectDescendants(child, memberName, result);
                    }
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<Object>) node) {
                if (memberName == null) {
                    result.add(item);
                }
                if (item instanceof Map || item instanceof List) {
                    collectDescendants(item, memberName, result);
                }
            }
        }
    }

    // ===================== 过滤器求值 =====================

    @SuppressWarnings("unchecked")
    private static boolean evalFilter(FilterExpr expr, Object current, Object root) {
        return switch (expr) {
            case Comparison(var left, var op, var right) -> {
                // 处理 truthiness 检查：@.key（无比较操作符）→ left != false（真理值判断）
                if (right instanceof LiteralTerm rl && rl.value() instanceof Boolean b && !b && op == CmpOp.NE) {
                    Object lv = evalTerm(left, current, root);
                    yield isTruthy(lv);
                }
                Object lv = evalTerm(left, current, root);
                Object rv = evalTerm(right, current, root);
                yield compare(lv, rv, op);
            }
            case LogicalAnd(var l, var r) -> evalFilter(l, current, root) && evalFilter(r, current, root);
            case LogicalOr(var l, var r) -> evalFilter(l, current, root) || evalFilter(r, current, root);
            case LogicalNot(var e) -> !evalFilter(e, current, root);
        };
    }

    private static Object evalTerm(FilterTerm term, Object current, Object root) {
        return switch (term) {
            case LiteralTerm(var value) -> value;
            case PathTerm(var absolute, var segs) -> {
                Object node = absolute ? root : current;
                if (!segs.isEmpty()) {
                    List<Object> results = evaluate(node, segs);
                    yield results.isEmpty() ? null : results.get(0);
                }
                yield node;
            }
            case FunctionTerm(var name, var absolute, var segs) -> {
                Object node = absolute ? root : current;
                if (!segs.isEmpty()) {
                    List<Object> results = evaluate(node, segs);
                    node = results.isEmpty() ? null : results.get(0);
                }
                if ("length".equals(name)) {
                    if (node instanceof String) yield (long) ((String) node).length();
                    if (node instanceof List) yield (long) ((List) node).size();
                    if (node instanceof Map) yield (long) ((Map) node).size();
                    yield 0L;
                }
                if ("count".equals(name)) {
                    // RFC 9535：count 必须带一个函数参数（nodelist）；无参数视为非法。
                    if (segs.isEmpty()) {
                        throw new IllegalStateException("JSONPath 函数 count() 必须带参数");
                    }
                    Object n = absolute ? root : current;
                    List<Object> results = evaluate(n, segs);
                    yield (long) results.size();
                }
                yield 0L;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static boolean compare(Object l, Object r, CmpOp op) {
        // null 处理
        if (l == null || r == null) {
            boolean bothNull = (l == null && r == null);
            return switch (op) {
                case EQ -> bothNull;
                case NE -> !bothNull;
                default -> false;
            };
        }

        // 数值比较
        if (l instanceof Number && r instanceof Number) {
            double ld = ((Number) l).doubleValue();
            double rd = ((Number) r).doubleValue();
            return switch (op) {
                case EQ -> ld == rd;
                case NE -> ld != rd;
                case LT -> ld < rd;
                case LE -> ld <= rd;
                case GT -> ld > rd;
                case GE -> ld >= rd;
            };
        }

        // 字符串比较
        if (l instanceof String && r instanceof String) {
            int cmp = ((String) l).compareTo((String) r);
            return switch (op) {
                case EQ -> cmp == 0;
                case NE -> cmp != 0;
                case LT -> cmp < 0;
                case LE -> cmp <= 0;
                case GT -> cmp > 0;
                case GE -> cmp >= 0;
            };
        }

        // 布尔比较
        if (l instanceof Boolean && r instanceof Boolean) {
            int cmp = ((Boolean) l).compareTo((Boolean) r);
            return switch (op) {
                case EQ -> cmp == 0;
                case NE -> cmp != 0;
                default -> false;
            };
        }

        // 不同类型或不可比 — 仅 EQ/NE 可能返回 true
        if (op == CmpOp.EQ) return l.equals(r);
        if (op == CmpOp.NE) return !l.equals(r);
        return false;
    }

    /** 判断值是否为「真」（RFC 9535 真理值语义）。 */
    private static boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof String) return !((String) v).isEmpty();
        if (v instanceof List) return !((List<?>) v).isEmpty();
        if (v instanceof Map) return !((Map<?, ?>) v).isEmpty();
        return true;
    }
}
