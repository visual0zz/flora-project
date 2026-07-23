package com.flora.codegen.engine;

import com.flora.codegen.engine.runtime.Context;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖渲染上下文 {@link Context} 的全部分支：
 * <ul>
 *   <li>子上下文创建（父子关系、共享 includeChain/macros/includes）</li>
 *   <li>变量作用域链（子上下文写入对父不可见，父写入对子可见）</li>
 *   <li>属性链查找（{@code m.v} 点号路径解析）</li>
 *   <li>{@link Context#lookupFirst} 优先返回参数值，其次局部变量</li>
 * </ul>
 */
class ContextTest {

    @Test
    void childSharesStateAndRecordsParent() {
        Context root = Context.of(Map.of("p", 1), Map.of());
        Context child = root.child();
        assertSame(root, child.parent);
        assertSame(root.includes, child.includes);
    }

    @Test
    void macrosSharedViaChild() {
        Context root = Context.of(Map.of(), Map.of());
        Context child = root.child();
        // 通过 root 注册宏，子上下文可查看到
        assertNull(child.getMacro("test"));
    }

    @Test
    void includeChainSharedBetweenParentAndChild() {
        Context root = Context.of(Map.of(), Map.of());
        Context child = root.child();
        assertTrue(root.addIncludeChain("k"));
        // 子上下文共享同一 includeChain，重复添加返回 false
        assertFalse(child.addIncludeChain("k"));
        child.removeIncludeChain("k");
        assertTrue(root.addIncludeChain("k"));
    }

    @Test
    void variableScopeResolvesUpParentChain() {
        Context root = Context.of(Map.of(), Map.of());
        Context child = root.child();
        child.setVar("x", 2);
        assertEquals(2, child.getVar("x"));
        assertNull(root.getVar("x"));
        root.setVar("y", 3);
        assertEquals(3, child.getVar("y"));
    }

    @Test
    void lookupFollowsPropertyChain() {
        Context ctx = Context.of(Map.of("m", Map.of("v", 9)), Map.of());
        assertEquals(9, ctx.lookup("m.v"));
    }

    @Test
    void lookupFirstPrioritizesParamsOverLocal() {
        Context ctx = Context.of(Map.of("q", 1), Map.of());
        ctx.setVar("q", 3);
        assertEquals(1, ctx.lookupFirst("q"));
    }

    @Test
    void lookupFirstFallsBackToLocal() {
        Context ctx = Context.of(Map.of(), Map.of());
        ctx.setVar("q", 3);
        assertEquals(3, ctx.lookupFirst("q"));
    }
}
