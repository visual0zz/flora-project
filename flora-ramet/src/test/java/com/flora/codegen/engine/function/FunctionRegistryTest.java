package com.flora.codegen.engine.function;

import com.flora.codegen.LazyArg;
import com.flora.codegen.TemplateFunction;
import com.flora.codegen.engine.runtime.FunctionRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 覆盖函数注册表 FunctionRegistry：内置注册、SPI 加载、查找与自定义注册。
 */
class FunctionRegistryTest {

    @Test
    void builtinsRegisteredAndSpiLoaded() {
        // 构造时执行内置函数注册与 ServiceLoader 加载，覆盖相关分支
        FunctionRegistry reg = new FunctionRegistry();
        assertNotNull(reg.get("capitalize"));
        assertNotNull(reg.get("lowercase"));
        assertNotNull(reg.get("uppercase"));
        assertNotNull(reg.get("javaString"));
    }

    @Test
    void unknownFunctionReturnsNull() {
        assertNull(new FunctionRegistry().get("nope"));
    }

    @Test
    void customFunctionCanBeRegistered() {
        FunctionRegistry reg = new FunctionRegistry();
        TemplateFunction fn = new TemplateFunction() {
            @Override
            public String name() {
                return "rev";
            }

            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object apply(List<LazyArg> args) {
                Object v = args.get(0).eval();
                String s = v == null ? "" : v.toString();
                return new StringBuilder(s).reverse().toString();
            }
        };
        reg.register(fn);
        assertSame(fn, reg.get("rev"));
    }

    @Test
    void registryIsWiredIntoContextViaParse() throws IOException {
        // 通过完整解析路径验证注册表在渲染期可用
        com.flora.codegen.CodeGenUtil.Generated g = com.flora.codegen.CodeGenUtil.generate(
                "<#meta>@Param{ name: \"bo\" } @Path{ \"C.java\" }</#meta>${capitalize(name)}",
                Map.of()).get(0);
        assertTrue(g.content().contains("Bo"), g.content());
    }
}
