package com.flora.codec.json;

import java.util.List;

// ====== 路径选择器 ======

sealed interface Selector permits NameSelector, IndexSelector, MultiIndexSelector,
        SliceSelector, WildcardSelector, DescendantSelector, FilterSelector {}

record NameSelector(String name) implements Selector {}
record IndexSelector(int index) implements Selector {}
record MultiIndexSelector(List<Integer> indices) implements Selector {}
record SliceSelector(Integer start, Integer end, Integer step) implements Selector {}
record WildcardSelector() implements Selector {}
record DescendantSelector(String memberName) implements Selector {}
record FilterSelector(FilterExpr expr) implements Selector {}

// ====== 过滤器表达式 ======

sealed interface FilterExpr permits Comparison, LogicalAnd, LogicalOr, LogicalNot {}

record Comparison(FilterTerm left, CmpOp op, FilterTerm right) implements FilterExpr {}
record LogicalAnd(FilterExpr left, FilterExpr right) implements FilterExpr {}
record LogicalOr(FilterExpr left, FilterExpr right) implements FilterExpr {}
record LogicalNot(FilterExpr expr) implements FilterExpr {}

enum CmpOp { EQ, NE, LT, LE, GT, GE }

sealed interface FilterTerm permits LiteralTerm, PathTerm, FunctionTerm {}

record LiteralTerm(Object value) implements FilterTerm {}
record PathTerm(boolean absolute, List<Selector> segments) implements FilterTerm {}
record FunctionTerm(String name, boolean absolute, List<Selector> segments) implements FilterTerm {}
