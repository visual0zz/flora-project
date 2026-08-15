package com.flora.shell.spec;

import com.flora.root.codec.json.model.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgParserTest {

    private static ArgParser portParser() {
        return new ArgParser(List.of(
                ArgSpec.builder().kind(ArgSpec.Kind.OPTION).name("port").shortName("p")
                        .type(ArgSpec.Type.INT).description("端口").build(),
                ArgSpec.builder().kind(ArgSpec.Kind.OPTION).name("verbose").type(ArgSpec.Type.BOOLEAN)
                        .description("详细").defaultValue(false).build(),
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("file").required(true)
                        .description("目标文件").build()));
    }

    @Test
    void parsesOptionsAndPositionals() {
        ParsedArgs args = portParser().parse(List.of("-p", "8080", "--verbose", "out.txt"));
        assertEquals(8080, args.getInt("port"));
        assertTrue(args.getBoolean("verbose"));
        assertEquals("out.txt", args.get("file"));
    }

    @Test
    void missingRequiredPositionalThrows() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> portParser().parse(List.of("--verbose")));
        assertTrue(ex.getMessage().contains("file"));
    }

    @Test
    void unknownOptionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> portParser().parse(List.of("--bogus", "x")));
    }

    @Test
    void intValueErrorThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> portParser().parse(List.of("--port", "abc", "f")));
    }

    @Test
    void negativeNumberIsNotAnOptionValue() {
        // 位置参数为数值时不应被当作选项
        ParsedArgs args = portParser().parse(List.of("--port", "-1", "f"));
        assertEquals(-1, args.getInt("port"));
        assertEquals("f", args.get("file"));
    }

    @Test
    void structuredValidationMatchesCli() {
        JsonObject params = new JsonObject()
                .put("port", 8080)
                .put("verbose", true)
                .put("file", "x");
        ParsedArgs args = portParser().validate(params);
        assertEquals(8080, args.getInt("port"));
        assertTrue(args.getBoolean("verbose"));
        assertEquals("x", args.get("file"));
    }

    @Test
    void mutexGroupRejectsBoth() {
        ArgParser p = new ArgParser(List.of(
                ArgSpec.builder().name("a").type(ArgSpec.Type.BOOLEAN).build(),
                ArgSpec.builder().name("b").type(ArgSpec.Type.BOOLEAN).build()));
        p.mutuallyExclusive("a", "b");
        assertThrows(IllegalArgumentException.class,
                () -> p.parse(List.of("--a", "--b")));
    }

    @Test
    void oneOfRequiresAtLeastOne() {
        ArgParser p = new ArgParser(List.of(
                ArgSpec.builder().name("x").type(ArgSpec.Type.BOOLEAN).build(),
                ArgSpec.builder().name("y").type(ArgSpec.Type.BOOLEAN).build()));
        p.oneOf("x", "y");
        assertThrows(IllegalArgumentException.class, () -> p.parse(List.of()));
        assertTrue(p.parse(List.of("--x")).getBoolean("x"));
    }

    @Test
    void variadicPositionalCollectsRest() {
        ArgParser p = new ArgParser(List.of(
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("cmd").required(true).build(),
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("rest").variadic(true)
                        .type(ArgSpec.Type.STRING_LIST).build()));
        ParsedArgs args = p.parse(List.of("go", "a", "b", "c"));
        assertEquals("go", args.get("cmd"));
        assertEquals(List.of("a", "b", "c"), args.getStringList("rest"));
    }
}
