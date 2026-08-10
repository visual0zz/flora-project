package com.flora.codec.json.impl;

import java.util.List;

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
