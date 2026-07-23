package com.flora.tangle;

import com.flora.classfile.ClassModel;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ObfuscatorTest {

    /** A 调用 B.value()，用于验证跨类成员引用在混淆后仍然有效。 */
    private static final String SRC_A = """
            package com.example;
            public class A {
                public int compute() {
                    return new B().value() + 1;
                }
            }
            """;

    private static final String SRC_B = """
            package com.example;
            public class B {
                public int value() { return 7; }
            }
            """;

    /** 含字符串字面量（含显式拼接，使字面量走 ldc 路径），用于验证字符串常量混淆。 */
    private static final String SRC_S = """
            package com.example;
            public class S {
                public String secret() { return "Hello, Tangle 混淆器!"; }
                public String greet(String who) {
                    return new StringBuilder().append("Hi ").append(who).toString();
                }
            }
            """;

    /** 含循环与分支，用于验证控制流混淆（混淆后行为不变、类仍可通过字节码校验）。 */
    private static final String SRC_CF = """
            package com.example;
            public class CF {
                public int sum(int n) {
                    int s = 0;
                    for (int i = 1; i <= n; i++) s += i;
                    if (s > 100) return s * 2;
                    return s;
                }
                public int absOf(long x) {
                    if (x < 0) return (int) (-x);
                    return (int) x;
                }
            }
            """;

    @Test
    void obfuscateRenamesClassesAndKeepsJarValid() throws Exception {
        byte[] jar = buildSampleJar();
        Obfuscator obf = new Obfuscator();
        obf.keepClassPrefix("com/example/A"); // 保留入口类 A，便于后面运行时校验
        byte[] out = obf.obfuscate(jar);

        // 1) 输出 jar 非空前；被重命名的类(com/example/B)应彻底消失，
        //    而被保留的类(com/example/A)应保留原名。
        assertTrue(out.length > 0);
        assertFalse(contains(out, "com/example/B"), "被重命名的原始类名不应残留");
        assertTrue(contains(out, "com/example/A"), "被保留的类名应原样保留");

        // 2) 输出里的每个 class 都能重新解析（结构合法）。
        Map<String, byte[]> classes = extractClasses(out);
        assertFalse(classes.isEmpty());
        for (byte[] bytes : classes.values()) {
            assertDoesNotThrow(() -> ClassModel.read(bytes), "混淆后的 class 必须能重新解析");
        }

        // 3) 至少存在一个被重命名的类（形如 a/...）。
        boolean sawRenamed = classes.keySet().stream().anyMatch(n -> n.startsWith("a/"));
        assertTrue(sawRenamed, "应至少重命名一个类");

        // 4) 运行时校验：加载入口类 A 与重命名后的 B，调用 A.compute() 仍返回 8。
        int result = runCompute(out);
        assertEquals(8, result, "跨类方法调用在混淆后仍需正确执行");
    }

    @Test
    void obfuscateStringConstantsHidesLiteralAndRuns() throws Exception {
        byte[] jar = buildStringJar();
        Obfuscator obf = new Obfuscator();
        obf.keepClassPrefix("com/example/S"); // 保留 S，便于运行时按签名加载
        byte[] out = obf.obfuscate(jar);

        Map<String, byte[]> classes = extractClasses(out);
        byte[] sBytes = classes.get("com/example/S");
        assertNotNull(sBytes, "类 S 应存在于输出 jar");

        // 1) 明文不应再以字面量出现在 class 文件中（已被 Base64+异或后的密文替代）。
        assertFalse(contains(sBytes, "Hello, Tangle 混淆器!"),
                "字符串明文不应再以字面量存在于 class 中");
        assertFalse(contains(sBytes, "Hi "), "被拼接的字符串常量也应被混淆");

        // 2) 运行时校验：defineClass 会触发字节码校验（解密方法与 StackMapTable 必须合法），
        //    且解密后返回值与原文一致。
        String secret = runStringMethod(out, "com.example.S", 0);
        assertEquals("Hello, Tangle 混淆器!", secret, "字符串常量混淆后仍可正确还原");
        String greet = runStringMethod(out, "com.example.S", 1, "World");
        assertEquals("Hi World", greet, "含常量拼接的方法混淆后也应正确还原");
    }

    // ===== 辅助：内存编译 + 打 jar =====

    private static byte[] buildSampleJar() throws IOException {
        File tmp = Files.createTempDirectory("tangle-sample").toFile();
        writeSrc(tmp, "com/example/A.java", SRC_A);
        writeSrc(tmp, "com/example/B.java", SRC_B);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends javax.tools.JavaFileObject> units =
                    fm.getJavaFileObjectsFromFiles(List.of(
                            new File(tmp, "com/example/A.java"),
                            new File(tmp, "com/example/B.java")));
            boolean ok = compiler.getTask(null, fm, null, null, null, units).call();
            assertTrue(ok, "样本源码编译失败");
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            addClass(zos, tmp, "com/example/A.class");
            addClass(zos, tmp, "com/example/B.class");
        }
        return bos.toByteArray();
    }

    private static void writeSrc(File base, String path, String code) throws IOException {
        File f = new File(base, path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), code);
    }

    private static void addClass(ZipOutputStream zos, File base, String path) throws IOException {
        byte[] bytes = Files.readAllBytes(new File(base, path).toPath());
        zos.putNextEntry(new ZipEntry(path));
        zos.write(bytes);
        zos.closeEntry();
    }

    private static byte[] buildStringJar() throws IOException {
        File tmp = Files.createTempDirectory("tangle-string").toFile();
        writeSrc(tmp, "com/example/S.java", SRC_S);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends javax.tools.JavaFileObject> units =
                    fm.getJavaFileObjectsFromFiles(List.of(new File(tmp, "com/example/S.java")));
            boolean ok = compiler.getTask(null, fm, null, null, null, units).call();
            assertTrue(ok, "字符串样本源码编译失败");
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            addClass(zos, tmp, "com/example/S.class");
        }
        return bos.toByteArray();
    }

    private static byte[] buildCFJar() throws IOException {
        File tmp = Files.createTempDirectory("tangle-cf").toFile();
        writeSrc(tmp, "com/example/CF.java", SRC_CF);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends javax.tools.JavaFileObject> units =
                    fm.getJavaFileObjectsFromFiles(List.of(new File(tmp, "com/example/CF.java")));
            boolean ok = compiler.getTask(null, fm, null, null, null, units).call();
            assertTrue(ok, "控制流样本源码编译失败");
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            addClass(zos, tmp, "com/example/CF.class");
        }
        return bos.toByteArray();
    }

    // ===== 辅助：从 jar 抽取 class =====

    private static Map<String, byte[]> extractClasses(byte[] jar) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().endsWith(".class")) {
                    map.put(e.getName().substring(0, e.getName().length() - 6), zis.readAllBytes());
                }
            }
        }
        return map;
    }

    private static boolean contains(byte[] data, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.UTF_8);
        if (n.length == 0) {
            return true;
        }
        for (int i = 0; i + n.length <= data.length; i++) {
            boolean ok = true;
            for (int j = 0; j < n.length; j++) {
                if (data[i + j] != n[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return true;
            }
        }
        return false;
    }

    // ===== 辅助：加载并运行 =====

    private static int runCompute(byte[] jar) throws Exception {
        Map<String, byte[]> classes = extractClasses(jar);
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = classes.get(name.replace('.', '/'));
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, b, 0, b.length);
            }
        };
        Class<?> aClass = loader.loadClass("com.example.A");
        // 找到 A 中返回 int、无参的 public 方法（即被混淆前的 compute）。
        for (java.lang.reflect.Method m : aClass.getDeclaredMethods()) {
            if (m.getReturnType() == int.class && m.getParameterCount() == 0) {
                m.setAccessible(true);
                return (int) m.invoke(aClass.getDeclaredConstructor().newInstance());
            }
        }
        throw new AssertionError("未在 A 中找到可调用的方法");
    }

    /** 加载并调用 com.example.S 中返回 String、参数个数为 paramCount 的 public 方法（方法名在混淆后被改名，故按签名查找）。 */
    private static String runStringMethod(byte[] jar, String className, int paramCount, String... arg) throws Exception {
        Map<String, byte[]> classes = extractClasses(jar);
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = classes.get(name.replace('.', '/'));
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, b, 0, b.length);
            }
        };
        Class<?> c = loader.loadClass(className);
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())
                    && m.getReturnType() == String.class
                    && m.getParameterCount() == paramCount) {
                m.setAccessible(true);
                Object[] args = (arg == null || arg.length == 0) ? new Object[0] : arg;
                return (String) m.invoke(c.getDeclaredConstructor().newInstance(), args);
            }
        }
        throw new AssertionError("未在 " + className + " 中找到匹配的 String 方法");
    }

    @Test
    void obfuscateControlFlowKeepsBehavior() throws Exception {
        byte[] jar = buildCFJar();
        Obfuscator obf = new Obfuscator();
        obf.setStringObfuscation(false); // 隔离验证控制流混淆
        obf.keepClassPrefix("com/example/CF");
        byte[] out = obf.obfuscate(jar);

        // 1) 每个 class 都能被 defineClass 重新加载（控制流改写后仍需通过字节码校验，
        //    可达代码偏移不变，仅末尾追加了不可达诱饵块，无需重建 StackMapTable）。
        Map<String, byte[]> classes = extractClasses(out);
        assertFalse(classes.isEmpty());
        for (byte[] bytes : classes.values()) {
            assertDoesNotThrow(() -> ClassModel.read(bytes), "控制流混淆后的 class 必须能重新解析");
        }

        // 2) 运行时行为不变：按签名查找并调用（方法名被改名，故按描述符查找）。
        int sum10 = runIntMethod(out, "com.example.CF", int.class, 10);
        assertEquals(55, sum10, "sum(10) 应为 55");
        int sumNeg = runIntMethod(out, "com.example.CF", int.class, -5);
        assertEquals(0, sumNeg, "sum(-5) 循环不执行应返回 0");
        int absNeg = runIntMethod(out, "com.example.CF", long.class, -5L);
        assertEquals(5, absNeg, "absOf(-5) 应为 5");
        int absPos = runIntMethod(out, "com.example.CF", long.class, 3L);
        assertEquals(3, absPos, "absOf(3) 应为 3");
    }

    /** 加载并调用指定类中“返回 int、单个参数类型为 paramType”的 public 方法（按签名查找，方法名可能已改名）。 */
    private static int runIntMethod(byte[] jar, String className, Class<?> paramType, Object arg) throws Exception {
        Map<String, byte[]> classes = extractClasses(jar);
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = classes.get(name.replace('.', '/'));
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, b, 0, b.length);
            }
        };
        Class<?> c = loader.loadClass(className);
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())
                    && m.getReturnType() == int.class
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == paramType) {
                m.setAccessible(true);
                return (int) m.invoke(c.getDeclaredConstructor().newInstance(), arg);
            }
        }
        throw new AssertionError("未在 " + className + " 中找到匹配的 int 方法");
    }
}
