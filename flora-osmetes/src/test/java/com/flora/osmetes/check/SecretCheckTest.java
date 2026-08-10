package com.flora.osmetes.check;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.FileCheck;
import com.flora.osmetes.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SecretCheck} 的综合判定测试：扫描字符串字面量与裸标量候选，
 * 厂商前缀 / 熵→ERROR，占位符 / 正则 / 格式串 / 常规结构→豁免。
 */
class SecretCheckTest {

    @TempDir
    Path tmp;

    private List<CheckIssue> run(String content) throws IOException {
        return runAs("Sample.java", content);
    }

    private List<CheckIssue> runAs(String fileName, String content) throws IOException {
        Path file = tmp.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        List<CheckIssue> sink = new ArrayList<>();
        new SecretCheck().check(file, fileName, sink);
        return sink;
    }

    @Test
    void plainShortStringValueIsSilent() throws IOException {
        // 普通短字符串（低于最小长度阈值）不是密钥
        assertTrue(run("class C { String password = \"helloWorld123\"; }").isEmpty(),
                "普通短字符串不应报告: ");
    }

    @Test
    void keyLikeSecretButValueMockIsSilent() throws IOException {
        // 键名像密钥，但值是 mock 假数据 → 整体豁免
        assertTrue(run("class C { String password = \"test\"; }").isEmpty(),
                "值像 mock 应豁免: ");
        assertTrue(run("class C { String apiKey = \"example-key-xxxx\"; }").isEmpty(),
                "值像 mock 应豁免: ");
        assertTrue(run("class C { String secret = \"<your-secret>\"; }").isEmpty(),
                "占位符应豁免: ");
    }

    @Test
    void valueLikeSecretReportsError() throws IOException {
        // 值带典型厂商前缀（Stripe），即使键名普通也应 ERROR
        List<CheckIssue> issues = run("class C { String data = \"sk-aB3kF9xQ2mNpLr7tVcWz\"; }");
        assertEquals(1, issues.size(), "值像真实密钥应报 ERROR: " + issues);
        assertEquals(Severity.ERROR, issues.getFirst().severity());
    }

    @Test
    void valueLikeSecretByHighEntropyReportsError() throws IOException {
        // 值无前缀但长且字母数字混合，判为高熵密钥串
        List<CheckIssue> issues = run("class C { String token = \"aB3kF9xQ2mNpLr7tVcWzQe\"; }");
        assertEquals(1, issues.size(), "高熵值应报 ERROR: " + issues);
        assertEquals(Severity.ERROR, issues.getFirst().severity());
    }

    @Test
    void uuidValueIsNotTreatedAsSecret() throws IOException {
        // UUID 虽高熵，但属常规标识符，不应判为密钥
        assertTrue(run("class C { String secret = \"550e8400-e29b-41d4-a716-446655440000\"; }").isEmpty(),
                "UUID 值不应报告: ");
    }

    @Test
    void hashValueIsNotTreatedAsSecret() throws IOException {
        // 长 hex 哈希属常规摘要结构，不应判为密钥
        assertTrue(run("class C { String token = \"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"; }")
                        .isEmpty(),
                "哈希值不应报告: ");
    }

    @Test
    void timestampValueIsSilent() throws IOException {
        // ISO 时间戳含大写字母与数字但缺小写，字符类别不足，不应判为密钥
        assertTrue(run("class C { String ts = \"2024-01-15T103000Z\"; }").isEmpty(),
                "时间戳值不应报告: ");
    }

    @Test
    void versionLikeValueIsSilent() throws IOException {
        // 版本串含小写与数字两类，字符类别不足，不应误报
        assertTrue(run("class C { String version = \"20240803release12\"; }").isEmpty(),
                "版本串不应误报: ");
    }

    @Test
    void hyphenatedPlainWordIsSilent() throws IOException {
        // 连字符普通词（单字符类别）不是密钥；键名不再作为判定信号
        assertTrue(run("class C { String client_secret = \"normal-text-here\"; }").isEmpty(),
                "连字符普通词不应报告: ");
    }

    @Test
    void normalAssignmentIsSilent() throws IOException {
        assertTrue(run("class C { int x = 5; String name = \"alice\"; }").isEmpty(),
                "普通赋值不应报告: ");
    }

    @Test
    void variableReferenceIsSilent() throws IOException {
        // 字段赋值管道：右值是变量引用而非字面量，不构成硬编码
        assertTrue(run("class C { void f(String password) { this.password = password; } }").isEmpty(),
                "变量引用赋值不应报告: ");
    }

    @Test
    void numericValueIsSilent() throws IOException {
        // 数值常量即使键名像密钥也不是硬编码密钥
        assertTrue(run("class C { static final int SECRET_LEN = 32; }").isEmpty(),
                "数值常量不应报告: ");
    }

    @Test
    void constructorExpressionIsSilent() throws IOException {
        // 右值是表达式（构造调用），不是字面量
        assertTrue(run("class C { Map<String, String> TOKEN_COLORS = new HashMap<>(); }").isEmpty(),
                "构造表达式不应报告: ");
    }

    @Test
    void bareScalarInPropertiesReportsError() throws IOException {
        // .properties 允许裸标量，高熵裸值按值形态判 ERROR
        List<CheckIssue> issues = runAs("app.properties", "db.password=aB3kF9xQ2mNpLr7tVcWz");
        assertEquals(1, issues.size(), "属性文件裸标量应报告: " + issues);
        assertEquals(Severity.ERROR, issues.getFirst().severity());
    }

    @Test
    void bareScalarPrefixInYamlReportsError() throws IOException {
        // YAML 裸标量带厂商前缀 → ERROR
        List<CheckIssue> issues = runAs("app.yaml", "data: sk-aB3kF9xQ2mNpLr7tVcWz");
        assertEquals(1, issues.size(), "YAML 裸标量前缀应报 ERROR: " + issues);
        assertEquals(Severity.ERROR, issues.getFirst().severity());
    }

    @Test
    void isoTimestampIsSilent() throws IOException {
        // 时间戳属常规公开结构，按结构豁免，不应报告
        assertTrue(run("class C { String ts = \"2024-01-15T10:30:00Z\"; }").isEmpty(),
                "ISO 时间戳不应报告: ");
        assertTrue(run("class C { String ts = \"20240115T103000Z\"; }").isEmpty(),
                "紧凑时间戳不应报告: ");
    }

    @Test
    void hexDigestIsSilent() throws IOException {
        // 64 位 hex 是合法摘要形态，按结构豁免
        assertTrue(run("class C { String secretKey = \"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"; }")
                        .isEmpty(),
                "hex 摘要不应报告: ");
    }

    @Test
    void urlValueIsSilentForKeyName() throws IOException {
        // URL 是常规结构，值形态豁免；键名普通则不报
        assertTrue(run("class C { String url = \"https://maven.apache.org/POM/4.0.0\"; }").isEmpty(),
                "URL 值不应误报: ");
    }

    @Test
    void urlEmbeddingCredentialReportsError() throws IOException {
        // URL 内嵌 user:pass 是真泄露，提示性 negative lookahead 不豁免 → 报 ERROR
        List<CheckIssue> issues = run("class C { String url = \"https://admin:hunter2@internal.corp/api\"; }");
        assertEquals(1, issues.size(), "URL 内嵌凭据应报 ERROR: " + issues);
        assertEquals(Severity.ERROR, issues.getFirst().severity());
    }

    @Test
    void repeatedCharValueIsSilent() throws IOException {
        // xxxx / **** 这类单字符主导的打码串整体豁免
        assertTrue(run("class C { String token = \"xxxx\"; }").isEmpty(),
                "打码串不应报告: ");
        assertTrue(run("class C { String token = \"****\"; }").isEmpty(),
                "打码串不应报告: ");
    }

    @Test
    void algorithmNameInValueIsSilent() throws IOException {
        // JCA 变换名是算法标识，非密钥材料
        assertTrue(run("class C { String alg = \"PBKDF2WithHmacSHA256\"; }").isEmpty(),
                "算法名不应报: ");
    }

    @Test
    void alphabetStringIsSilent() throws IOException {
        // 字母表常量（随机串字符池）虽长且混合类别，但含连续递增段，按结构豁免
        assertTrue(run("class C { String ALNUM = \"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\"; }")
                        .isEmpty(),
                "字母表串不应报: ");
    }

    @Test
    void naturalLanguagePhraseIsSilent() throws IOException {
        // 自然语言短语不是随机密钥
        assertTrue(run("class C { String msg = \"hello world 12390\"; }").isEmpty(),
                "自然语言短语不应报: ");
    }

    @Test
    void multilineValueIsSilent() throws IOException {
        // 含换行转义的多行拼接文本不当作单一密钥字面量
        assertTrue(run("class C { String manifest = \"Manifest-Version: 1.0\\nMain-Class: X\\n\"; }")
                        .isEmpty(),
                "多行文本不应报: ");
    }

    @Test
    void regexStringIsSilent() throws IOException {
        // 正则字面量含正则元字符，不是密钥
        assertTrue(run("class C { Pattern p = Pattern.compile(\"[a-zA-Z0-9_-]{16,}\"); }").isEmpty(),
                "正则字符串不应报告: ");
    }

    @Test
    void formatStringIsSilent() throws IOException {
        // 格式串（% 占位符）不是密钥
        assertTrue(run("class C { String fmt = \"%s-%d-%s\"; }").isEmpty(),
                "格式串不应报告: ");
    }

    @Test
    void methodCallArgumentSecretReportsError() throws IOException {
        // 非赋值形态（方法参数）中的厂商前缀密钥也能被发现
        List<CheckIssue> issues = run("class C { void f() { client.setToken(\"sk-aB3kF9xQ2mNpLr7tVcWz\"); } }");
        assertEquals(1, issues.size(), "方法参数中的前缀密钥应报 ERROR: " + issues);
        assertEquals(Severity.ERROR, issues.getFirst().severity());
    }

    @Test
    void implementsFileCheckContract() {
        FileCheck check = new SecretCheck();
        assertEquals("secret", check.name());
        assertFalse(check.fileExtensions().isEmpty());
    }
}
