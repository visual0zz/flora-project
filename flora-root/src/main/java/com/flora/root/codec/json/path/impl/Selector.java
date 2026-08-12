package com.flora.root.codec.json.path.impl;

import java.util.List;

/** JSONPath 路径选择器（RFC 9535），由解析器产出、评估器执行。 */
public sealed interface Selector permits NameSelector, IndexSelector, MultiIndexSelector,
        SliceSelector, WildcardSelector, DescendantSelector, FilterSelector {}

record NameSelector(String name) implements Selector {}
record IndexSelector(int index) implements Selector {}
record MultiIndexSelector(List<Integer> indices) implements Selector {}
record SliceSelector(Integer start, Integer end, Integer step) implements Selector {}
record WildcardSelector() implements Selector {}
record DescendantSelector(String memberName) implements Selector {}
record FilterSelector(FilterExpr expr) implements Selector {}
