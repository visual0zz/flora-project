package com.flora.internal.evaluation;

import com.flora.mock.jsonschema.JsonGenerator;
import com.flora.mock.regex.RegexStringGenerator;

/**
 * 字符串/JSON 生成器的人工评估入口。
 * <p>调用 {@link RegexStringGenerator} 与 {@link JsonGenerator} 生成若干样本并打印到控制台,
 * 供人工直接判断生成结果的合理性与多样性。</p>
 */
public final class MockGeneratorsEvaluation {

    private MockGeneratorsEvaluation() {
    }

    public static void main(String[] args) {
        evaluateRegex();
        evaluateJson();
    }

    /** 正则字符串生成:对每组 pattern 生成 5 条样本。 */
    private static void evaluateRegex() {
        System.out.println("==================== Regex 字符串生成 ====================");
        String[] patterns = {
                "[a-z]{2,4}",
                "\\d{3}-\\d{4}",
                "[A-Z][a-z]+ [A-Z][a-z]+",
                "0\\d{2}-\\d{8}",
                "(ab|cd)+",
                "\\w+@\\w+\\.(com|org|cn)",
                "[1-9]\\d{5}",
                "[0-9a-f]{8}",
                "[a-z&&[^aeiou]]+",
                "[^0-9]{3,6}",
        };
        for (String pattern : patterns) {
            RegexStringGenerator generator = RegexStringGenerator.of(pattern);
            System.out.println("pattern: " + pattern);
            for (int i = 0; i < 5; i++) {
                System.out.println("  " + generator.generate());
            }
        }
    }

    /** JSON 数据生成:对每组 schema 生成 3 条样本。 */
    private static void evaluateJson() {
        System.out.println("==================== JSON 数据生成 ====================");
        String[] schemas = {
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},"
                        + "\"age\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":120},"
                        + "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":5}},"
                        + "\"required\":[\"name\"]}",
                "{\"type\":\"string\",\"minLength\":2,\"maxLength\":12}",
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\",\"pattern\":\"[A-Z]{2}\\\\d{4}\"},"
                        + "\"score\":{\"type\":\"number\",\"minimum\":0,\"maximum\":100}},"
                        + "\"required\":[\"id\",\"score\"]}",
                "{\"type\":\"array\",\"items\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10},"
                        + "\"minItems\":3,\"maxItems\":8,\"uniqueItems\":true}",
                "{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"number\"}]}",
        };
        for (String schema : schemas) {
            JsonGenerator generator = JsonGenerator.of(schema);
            System.out.println("schema: " + schema);
            for (int i = 0; i < 3; i++) {
                System.out.println("  " + generator.generateStr());
            }
        }
    }
}
