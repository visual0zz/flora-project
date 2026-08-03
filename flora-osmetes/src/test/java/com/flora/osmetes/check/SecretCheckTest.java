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
 * {@link SecretCheck} 的综合判定测试：键名像密钥→WARNING，值像真实密钥→ERROR，
 * 值像 mock 假数据整体豁免。
 */
class SecretCheckTest {

    @TempDir
    Path tmp;

    private List<CheckIssue> run(String content) throws IOException {
        Path file = tmp.resolve("Sample.java");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        List<CheckIssue> sink = new ArrayList<>();
        new SecretCheck().check(file, "Sample.java", sink);
        return sink;
    }

    @Test
    void keyLikeSecretReportsWarning() throws IOException {
        // 键名像密钥，值是普通短串（非密钥、非 mock）
        List<CheckIssue> issues = run("class C { String password = \"helloWorld123\"; }");
        assertEquals(1, issues.size(), "键名像密钥应报 WARNING: " + issues);
        assertEquals(Severity.WARNING, issues.getFirst().severity());
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
        // UUID 虽高熵，但属常规标识符，不应判为密钥（最多因键名 WARNING）
        List<CheckIssue> issues = run(
                "class C { String secret = \"550e8400-e29b-41d4-a716-446655440000\"; }");
        assertFalse(issues.stream().anyMatch(i -> i.severity() == Severity.ERROR),
                "UUID 值不应报 ERROR: " + issues);
        assertEquals(1, issues.size(), "仅因键名报 WARNING: " + issues);
        assertEquals(Severity.WARNING, issues.getFirst().severity());
    }

    @Test
    void normalAssignmentIsSilent() throws IOException {
        assertTrue(run("class C { int x = 5; String name = \"alice\"; }").isEmpty(),
                "普通赋值不应报告: ");
    }

    @Test
    void implementsFileCheckContract() {
        FileCheck check = new SecretCheck();
        assertEquals("secret", check.name());
        assertFalse(check.fileExtensions().isEmpty());
    }
}
