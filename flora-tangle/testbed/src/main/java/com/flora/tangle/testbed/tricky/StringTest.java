package com.flora.tangle.testbed.tricky;

/**
 * 字符串操作测试类，用于验证字符串加密混淆功能。
 * <p>
 * 包含大量字符串常量定义、StringBuilder 拼接、String.format()、
 * substring()/replace()/trim()/split() 等操作，为混淆器提供
 * 丰富的字符串操作场景。
 * </p>
 */
public final class StringTest {

    /** 短文本常量 */
    private static final String SHORT = "Hello";

    /** 长文本常量 - 中文段落 */
    private static final String LONG_CN = "在软件开发中，代码混淆是一种重要的安全防护手段。" +
            "它通过对程序代码进行变换，使得代码难以被逆向工程理解。" +
            "字符串加密是混淆技术中的一个关键环节，可以防止攻击者通过搜索字符串" +
            "来定位关键逻辑。";

    /** 长文本常量 - 英文段落 */
    private static final String LONG_EN = "Code obfuscation is a technique used to protect software " +
            "from reverse engineering. It transforms the original source code into a " +
            "functionally equivalent but much harder to understand version.";

    /** 包含特殊字符的字符串 */
    private static final String SPECIAL = "!@#$%^&*()_+-=[]{}|;':\",./<>?~`";

    /** HTML 标签字符串 */
    private static final String HTML_FRAG = "<div class=\"content\">Hello &amp; World</div>";

    /** Unicode 字符串 */
    private static final String UNICODE = "日本語\t한국어\n中文 Ελληνικά Русский";

    /** 带前后空格的字符串 */
    private static final String PADDED = "   spaced out   ";

    /** CSV 格式字符串 */
    private static final String CSV_LINE = "apple,banana,cherry,durian,elderberry,fig,grape";

    /**
     * 执行所有字符串操作并返回结果摘要。
     *
     * @return 格式为 "StringTest:OK:长度=X" 的结果字符串
     */
    public static String test() {
        int totalLen = 0;

        // ---- 1. 字符串常量长度累加 ----
        totalLen += SHORT.length();
        totalLen += LONG_CN.length();
        totalLen += LONG_EN.length();
        totalLen += SPECIAL.length();
        totalLen += HTML_FRAG.length();
        totalLen += UNICODE.length();

        // ---- 2. StringBuilder 拼接 ----
        StringBuilder sb = new StringBuilder();
        sb.append(SHORT).append(", ");
        sb.append("你好");
        for (int i = 0; i < 5; i++) {
            sb.append("[").append(i).append("]");
        }
        sb.append("!");
        String concatenated = sb.toString();
        totalLen += concatenated.length();

        // ---- 3. String.format() ----
        String formatted = String.format("【统计】短=%d, 中文=%d, 英文=%d, 特殊=%d",
                SHORT.length(), LONG_CN.length(), LONG_EN.length(), SPECIAL.length());
        totalLen += formatted.length();

        // ---- 4. substring() ----
        String sub1 = LONG_CN.substring(0, 5);
        String sub2 = LONG_CN.substring(LONG_CN.length() - 3);
        String sub3 = LONG_EN.substring(10, 30);
        totalLen += sub1.length() + sub2.length() + sub3.length();

        // ---- 5. replace() ----
        String replaced1 = LONG_EN.replace("o", "0");
        String replaced2 = HTML_FRAG.replace("&amp;", "&");
        String replaced3 = UNICODE.replace("\t", "\\t").replace("\n", "\\n");
        totalLen += replaced1.length() + replaced2.length() + replaced3.length();

        // ---- 6. trim() ----
        String trimmed = PADDED.trim();
        int trimmedLen = trimmed.length();
        totalLen += trimmedLen;

        // ---- 7. split() ----
        String[] fruits = CSV_LINE.split(",");
        int fruitCount = fruits.length;
        StringBuilder fruitSb = new StringBuilder();
        for (String f : fruits) {
            fruitSb.append(f.trim()).append("|");
        }
        totalLen += fruitSb.length();

        // ---- 8. toUpperCase / toLowerCase / 更多转换 ----
        String upper = SHORT.toUpperCase();
        String lower = SHORT.toLowerCase();
        String reversed = new StringBuilder(SHORT).reverse().toString();
        totalLen += upper.length() + lower.length() + reversed.length();

        // ---- 9. valueOf / intern / repeat ----
        String fromInt = String.valueOf(42);
        String fromDouble = String.valueOf(3.14159);
        String repeat = "ha".repeat(5);
        totalLen += fromInt.length() + fromDouble.length() + repeat.length();

        // ---- 10. contains / startsWith / endsWith 断言（不改变长度，用于逻辑验证） ----
        boolean hasJava = LONG_EN.contains("Java");
        boolean hasCode = LONG_EN.contains("code");
        boolean startsWithHello = SHORT.startsWith("He");
        if (hasJava && hasCode && startsWithHello) {
            totalLen += 0; // 仅用于混淆条件判断
        }

        return "StringTest:OK:长度=" + totalLen + ",水果数=" + fruitCount + ",trim前=" + PADDED.length() + ",trim后=" + trimmedLen;
    }
}
