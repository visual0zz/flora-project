package com.flora.runtime.log;

import com.flora.runtime.log.impl.ConsoleAppender;
import com.flora.runtime.log.impl.FileAppender;
import com.flora.runtime.log.impl.LogMasker;
import com.flora.runtime.log.impl.LoggerImpl;
import com.flora.runtime.log.impl.MessageFormatter;
import com.flora.runtime.log.impl.RollingFileAppender;
import com.flora.runtime.log.spi.Appender;
import com.flora.runtime.log.spi.Layout;
import com.flora.runtime.log.spi.LogEvent;
import com.flora.runtime.log.spi.Masker;
import com.flora.runtime.log.spi.RollingPolicy;
import com.flora.runtime.virtual.filesys.VfsFileSystem;
import com.flora.runtime.virtual.filesys.backend.MemoryFileSystem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;


/**
 * 日志框架的完整单元测试。
 * 测试 Logger 工厂、消息格式化、布局格式、日志级别、Appender（控制台/文件）、级别继承、可加性、配置 API、参数化日志、MDC 及无配置场景。
 */
class LogTest {

    @AfterEach
    void tearDown() {
        LoggerFactory.reset();
        LoggerFactory.setDefaultMasker(Masker.NONE);
    }

    /**
     * 创建一个挂载于 /mem 的内存虚拟文件系统，用于无落盘的文件测试。
     *
     * @return 内存 VFS 实例（实现了 {@link java.nio.file.FileSystem}，可用 try-with-resources 关闭）
     */
    private VfsFileSystem newMemFs() {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        return fs;
    }

    // ==================== 获取 Logger ====================

    /**
     * 测试通过名称获取 Logger。
     */
    @Test
    void testGetLogger() {
        Logger log = LoggerFactory.getLogger("test");
        assertNotNull(log);
        assertEquals("test", log.getName());
    }

    /**
     * 测试通过 Class 获取 Logger，名称为类全限定名。
     */
    @Test
    void testGetLoggerByClass() {
        Logger log = LoggerFactory.getLogger(LogTest.class);
        assertEquals(LogTest.class.getName(), log.getName());
    }

    /**
     * 测试默认根日志级别为 DEBUG。
     */
    @Test
    void testDefaultRootLevelIsDebug() {
        assertEquals(Level.DEBUG, ((LoggerImpl) LoggerFactory.getRootLogger()).getEffectiveLevel());
    }

    // ==================== 消息格式化 ====================

    /**
     * 测试无参数的消息格式化。
     */
    @Test
    void testFormatNoArgs() {
        assertEquals("hello", MessageFormatter.format("hello", null));
        assertEquals("hello", MessageFormatter.format("hello", new Object[0]));
    }

    /**
     * 测试带占位符的消息格式化。
     */
    @Test
    void testFormatWithArgs() {
        assertEquals("hello world", MessageFormatter.format("hello {}", new Object[]{"world"}));
        assertEquals("a, b, c", MessageFormatter.format("{}, {}, {}", new Object[]{"a", "b", "c"}));
    }

    /**
     * 测试 null 参数格式化为 "null"。
     */
    @Test
    void testFormatWithNullArg() {
        assertEquals("hello null", MessageFormatter.format("hello {}", new Object[]{null}));
    }

    /**
     * 测试参数多于占位符时忽略多余参数。
     */
    @Test
    void testFormatMoreArgsThanPlaceholders() {
        assertEquals("hello world", MessageFormatter.format("hello {}", new Object[]{"world", "extra"}));
    }

    // ==================== 布局格式 ====================

    /**
     * 测试基本布局格式：%level %msg%n。
     */
    @Test
    void testLayoutBasic() {
        Layout layout = new Layout("%level %msg%n");
        LogEvent event = new LogEvent("test", Level.INFO, "hello", null, "hello");
        String result = layout.format(event);
        assertEquals("INFO hello" + System.lineSeparator(), result);
    }

    /**
     * 测试 %logger 占位符输出完整 Logger 名称。
     */
    @Test
    void testLayoutLoggerName() {
        Layout layout = new Layout("[%logger] %msg");
        LogEvent event = new LogEvent("com.example.MyClass", Level.INFO, "test", null, "test");
        String result = layout.format(event);
        assertEquals("[com.example.MyClass] test", result);
    }

    /**
     * 测试 %logger{N} 缩写 Logger 名称。
     */
    @Test
    void testLayoutLoggerAbbreviate() {
        Layout layout = new Layout("[%logger{20}] %msg");
        LogEvent event = new LogEvent("com.example.service.UserService", Level.INFO, "x", null, "x");
        String result = layout.format(event);
        assertTrue(result.startsWith("[c.e.s.UserService]"), result);
    }

    /**
     * 测试 %-5level 左对齐填充级别名称。
     */
    @Test
    void testLayoutLevelPadding() {
        Layout layout = new Layout("[%-5level] %msg");
        LogEvent event = new LogEvent("test", Level.INFO, "x", null, "x");
        String result = layout.format(event);
        assertEquals("[INFO ] x", result);
    }

    /**
     * 测试 %d{yyyy-MM-dd} 日期格式。
     */
    @Test
    void testLayoutDate() {
        Layout layout = new Layout("%d{yyyy-MM-dd} %msg");
        LogEvent event = new LogEvent("test", Level.INFO, "x", null, "x");
        String result = layout.format(event);
        assertTrue(result.startsWith("20"), result);
    }

    // ==================== 日志级别 ====================

    /**
     * 测试日志级别阈值过滤。
     */
    @Test
    void testLevelThreshold() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.WARN);
        Logger log = LoggerFactory.getLogger("thresholdTest");
        assertFalse(log.isInfoEnabled());
        assertTrue(log.isWarnEnabled());
        assertTrue(log.isErrorEnabled());
    }

    // ==================== ConsoleAppender ====================

    /**
     * 测试控制台 Appender 输出。
     */
    @Test
    void testConsoleAppender() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
        ConsoleAppender appender = new ConsoleAppender();
        appender.setLayout(new Layout("%level %msg%n"));
        ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
        Logger log = LoggerFactory.getLogger("consoleTest");
        log.info("console test message");
        log.error("error message");
    }

    // ==================== FileAppender ====================

    /**
     * 测试文件 Appender 输出到文件。
     */
    @Test
    void testFileAppender() throws IOException {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
        try (VfsFileSystem fs = newMemFs()) {
            Path file = fs.getPath("/mem/log.txt");
            FileAppender appender = new FileAppender(file);
            appender.setLayout(new Layout("%msg%n"));
            ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);

            Logger log = LoggerFactory.getLogger("fileTest");
            log.info("hello file");
            log.info("second line");
            appender.close();

            String content = Files.readString(file);
            assertTrue(content.contains("hello file"), "content=[" + content + "]");
            assertTrue(content.contains("second line"), "content=[" + content + "]");
        }
    }

    /**
     * 同一日志器上两个 appender 指向相同文件应被拒绝（配置期去重）。
     */
    @Test
    void testDuplicateAppenderPathRejected() {
        Logger logger = LoggerFactory.getLogger("dupPathTest");
        Path file = Path.of("target", "logtest", "dup.log");
        ((LoggerImpl) logger).addAppender(new FileAppender(file));

        FileAppender dup = new FileAppender(file);
        assertThrows(IllegalArgumentException.class,
                () -> ((LoggerImpl) logger).addAppender(dup),
                "同一日志器上重复的文件路径应被拒绝");

        // 相对/规范形式不同但指向同一文件，也应被识别为重复
        FileAppender relative = new FileAppender(
                Path.of("target", "logtest", "..", "logtest", "dup.log"));
        assertThrows(IllegalArgumentException.class,
                () -> ((LoggerImpl) logger).addAppender(relative),
                "规范后相同的文件路径应被识别为重复");

        // 不同路径则允许共存
        assertDoesNotThrow(() -> ((LoggerImpl) logger)
                .addAppender(new FileAppender(Path.of("target", "logtest", "other.log"))));
    }

    // ==================== 级别继承 ====================

    /**
     * 测试子 Logger 从父 Logger 继承级别。
     */
    @Test
    void testLevelInheritance() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
        Logger child = LoggerFactory.getLogger("com.example");
        assertEquals(Level.INFO, ((LoggerImpl) child).getEffectiveLevel());
        assertTrue(child.isInfoEnabled());

        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.DEBUG);
        assertEquals(Level.DEBUG, ((LoggerImpl) child).getEffectiveLevel());
    }

    /**
     * 测试子 Logger 覆盖父 Logger 的级别。
     */
    @Test
    void testChildOverridesLevel() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
        Logger child = LoggerFactory.getLogger("com.example");
        ((LoggerImpl) child).setLevel(Level.ERROR);
        assertEquals(Level.ERROR, ((LoggerImpl) child).getEffectiveLevel());
        assertFalse(child.isInfoEnabled());
        assertTrue(child.isErrorEnabled());
    }

    /**
     * 测试可加性：子 Logger 的日志是否传递给父 Logger。
     */
    @Test
    void testAdditivity() {
        LoggerFactory.reset();
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);

        CountingAppender rootAppender = new CountingAppender();
        ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(rootAppender);

        LoggerImpl child = (LoggerImpl) LoggerFactory.getLogger("com.example");
        child.setLevel(Level.INFO);
        CountingAppender childAppender = new CountingAppender();
        child.addAppender(childAppender);

        child.setAdditivity(true);
        child.info("test additivity true");
        assertEquals(1, childAppender.count, "child appender");
        assertEquals(1, rootAppender.count, "root via additivity");

        childAppender.count = 0;
        rootAppender.count = 0;
        child.setAdditivity(false);
        child.info("test additivity false");
        assertEquals(1, childAppender.count, "child appender still");
        assertEquals(0, rootAppender.count, "root NOT via additivity=false");
    }

    // ==================== 配置 API ====================

    /**
     * 测试通过 LogConfig 配置根日志级别。
     */
    @Test
    void testLogConfigRootLevel() {
        LogConfig.configure(cfg -> cfg.rootLevel(Level.WARN));
        Logger log = LoggerFactory.getLogger("test");
        assertFalse(log.isInfoEnabled());
        assertTrue(log.isWarnEnabled());
    }

    /**
     * 测试通过 LogConfig 配置文件 Appender。
     */
    @Test
    void testLogConfigFile() throws IOException {
        try (VfsFileSystem fs = newMemFs()) {
            Path tmpFile = fs.getPath("/mem/config.log");
            LogConfig.configure(cfg -> cfg
                    .rootLevel(Level.INFO)
                    .fileAppender(f -> f
                            .file(tmpFile)
                            .pattern("%msg%n")));

            Logger log = LoggerFactory.getLogger("configTest");
            log.info("config file test");
            log.info("second");
            ((LoggerImpl) LoggerFactory.getRootLogger()).getAppenders().forEach(Appender::close);

            String content = Files.readString(tmpFile);
            assertTrue(content.contains("config file test"), "content=[" + content + "]");
        }
    }

    /**
     * 测试通过 LogConfig 配置级别路由（不同级别输出到不同文件）。
     */
    @Test
    void testLogConfigLevelRouting() throws IOException {
        try (VfsFileSystem fs = newMemFs()) {
            Path errorFile = fs.getPath("/mem/error.log");
            Path infoFile = fs.getPath("/mem/info.log");

            LogConfig.configure(cfg -> cfg
                    .rootLevel(Level.INFO)
                    .fileAppender(f -> f
                            .file(infoFile)
                            .pattern("%msg%n"))
                    .levelRouting(Level.ERROR, rt -> rt
                            .file(errorFile)
                            .pattern("%msg%n")));

            Logger log = LoggerFactory.getLogger("routingTest");
            log.info("info msg");
            log.error("error msg");
            ((LoggerImpl) LoggerFactory.getRootLogger()).getAppenders().forEach(Appender::close);

            String infoContent = Files.readString(infoFile);
            String errorContent = Files.readString(errorFile);

            assertTrue(infoContent.contains("info msg"), "infoContent=[" + infoContent + "]");
            assertTrue(infoContent.contains("error msg"), "infoContent should have error msg too");
            assertTrue(errorContent.contains("error msg"), "errorContent=[" + errorContent + "]");
            assertFalse(errorContent.contains("info msg"), "errorContent should NOT have info msg");
        }
    }

    // ==================== 参数化日志 ====================

    /**
     * 测试参数化日志输出（占位符替换）。
     */
    @Test
    void testParameterizedLogging() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
        CountingAppender appender = new CountingAppender();
        ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
        Logger log = LoggerFactory.getLogger("paramTest");
        log.info("hello {}, you have {} messages", "Alice", 42);
        assertEquals(1, appender.count);
    }

    // ==================== MDC ====================

    /**
     * 测试 MDC 的基本 put/get/remove 操作。
     */
    @Test
    void testMDC() {
        MDC.put("userId", "123");
        assertEquals("123", MDC.get("userId"));
        MDC.remove("userId");
        assertNull(MDC.get("userId"));
    }

    /**
     * 测试 MDC 的线程隔离性。
     */
    @Test
    void testMDCThreadIsolation() throws InterruptedException {
        MDC.put("key", "main");
        Thread t = new Thread(() -> {
            assertNull(MDC.get("key"));
            MDC.put("key", "thread");
            assertEquals("thread", MDC.get("key"));
        });
        t.start();
        t.join();
        assertEquals("main", MDC.get("key"));
    }

    // ==================== 无配置 ====================

    /**
     * 测试未配置任何日志框架时仍能正常工作。
     */
    @Test
    void testNoConfigurationStillWorks() {
        Logger log = LoggerFactory.getLogger("noConfig");
        log.info("this should not throw");
        log.debug("also fine");
        log.error("still fine");
    }

    // ==================== 日志脱敏 ====================

    /**
     * 测试 Masker.NONE 原样返回，且不抛 null 指针。
     */
    @Test
    void testMaskerNoneReturnsOriginal() {
        assertEquals("secret=abc123", Masker.NONE.mask("secret=abc123"));
        assertNull(Masker.NONE.mask(null));
    }

    /**
     * 测试 LogMasker.DEFAULT 覆盖常见敏感片段。
     */
    @Test
    void testLogMaskerDefaults() {
        LogMasker m = LogMasker.DEFAULT;
        assertEquals("Bearer ********", m.mask("Bearer eyJhbGciOiJIUzI1NiJ9"));
        assertEquals("api_key=********", m.mask("api_key=abcdef12345678"));
        assertTrue(m.mask("http://user:pwd@example.com/path").contains("********@"));
        assertTrue(m.mask("contact alice@example.com").contains("****@****"));
        assertEquals("id ******** ok", m.mask("id 11010119900307123X ok"));
        assertEquals("card ******** ok", m.mask("card 4111111111111111 ok"));
    }

    /**
     * 测试 withRule 扩展规则时不会改动 DEFAULT 常量。
     */
    @Test
    void testLogMaskerWithRuleDoesNotMutateDefault() {
        LogMasker custom = LogMasker.DEFAULT.withRule("inner", "OUTER");
        assertTrue(custom.mask("inner").contains("OUTER"));
        assertEquals("inner", LogMasker.DEFAULT.mask("inner"));
    }

    /**
     * 测试日志器在格式化消息时应用脱敏器，敏感字段不会落到输出。
     */
    @Test
    void testLoggerAppliesMaskerToFileOutput() throws IOException {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
        try (VfsFileSystem fs = newMemFs()) {
            Path file = fs.getPath("/mem/mask.log");
            FileAppender appender = new FileAppender(file);
            appender.setLayout(new Layout("%msg%n"));
            ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
            LoggerFactory.setDefaultMasker(LogMaskers.DEFAULT);

            Logger log = LoggerFactory.getLogger("maskTest");
            log.info("login token=abcdef12345678 success");
            appender.close();
            String content = Files.readString(file);
            assertFalse(content.contains("abcdef12345678"), "secret must be masked: " + content);
            assertTrue(content.contains("token=********"), content);
        }
    }

    /**
     * 测试 LogConfig.mask 开启后，全局（含配置后新建的日志器）均生效。
     */
    @Test
    void testLogConfigMaskAppliesGlobally() throws IOException {
        try (VfsFileSystem fs = newMemFs()) {
            Path file = fs.getPath("/mem/config-mask.log");
            LogConfig.configure(cfg -> cfg
                    .rootLevel(Level.INFO)
                    .fileAppender(f -> f.file(file).pattern("%msg%n"))
                    .mask(LogMaskers.DEFAULT));

            Logger later = LoggerFactory.getLogger("createdAfterMask");
            later.info("password=hunter2abcdefghij");
            ((LoggerImpl) LoggerFactory.getRootLogger()).getAppenders().forEach(Appender::close);
            String content = Files.readString(file);
            assertFalse(content.contains("hunter2abcdefghij"), content);
        }
    }

    /**
     * 测试 mask(masker1, masker2...) 可同时应用默认规则与自定义规则。
     */
    @Test
    void testLogConfigMaskMultiple() throws IOException {
        try (VfsFileSystem fs = newMemFs()) {
            Path file = fs.getPath("/mem/config-mask-multi.log");
            Masker custom = text -> text.replace("TOPSECRET", "******");
            LogConfig.configure(cfg -> cfg
                    .rootLevel(Level.INFO)
                    .fileAppender(f -> f.file(file).pattern("%msg%n"))
                    .mask(LogMaskers.DEFAULT, custom));

            Logger later = LoggerFactory.getLogger("multiMask");
            later.info("password=hunter2abcdefghij and TOPSECRET here");
            ((LoggerImpl) LoggerFactory.getRootLogger()).getAppenders().forEach(Appender::close);
            String content = Files.readString(file);
            assertFalse(content.contains("hunter2abcdefghij"), content);
            assertFalse(content.contains("TOPSECRET"), content);
            assertTrue(content.contains("password=********"), content);
        }
    }

    // ==================== FATAL 级别 ====================

    /**
     * 测试 FATAL 级别高于 ERROR，且 gate 正确。
     * <p>FATAL 比 WARN/ERROR 更严重，因此 WARN 之下仍能输出 FATAL；
     * 而设为 FATAL 后 ERROR 因更严重程度不足被禁用。</p>
     */
    @Test
    void testFatalLevel() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.WARN);
        Logger log = LoggerFactory.getLogger("fatalTest");
        assertTrue(log.isFatalEnabled(), "WARN 之下应启用更严重的 FATAL");
        assertTrue(log.isErrorEnabled());

        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.FATAL);
        assertTrue(log.isFatalEnabled());
        assertFalse(log.isErrorEnabled(), "FATAL 之下不应启用较低的 ERROR");
    }

    /**
     * 测试 fatal 消息被记录且级别正确。
     */
    @Test
    void testFatalRecorded() {
        LoggerFactory.reset();
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.FATAL);
        CapturingAppender appender = new CapturingAppender();
        ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
        Logger log = LoggerFactory.getLogger("fatalRec");
        log.fatal("system down");
        assertNotNull(appender.last);
        assertEquals(Level.FATAL, appender.last.getLevel());
        assertEquals("system down", appender.last.getFormattedMessage());
    }

    // ==================== 异常关联 ====================

    /**
     * 测试 (String, Throwable) 重载把异常存入事件。
     */
    @Test
    void testThrowableStoredViaExplicitOverload() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
        CapturingAppender appender = new CapturingAppender();
        ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
        Logger log = LoggerFactory.getLogger("thTest");
        RuntimeException ex = new RuntimeException("boom");
        log.error("failed", ex);
        assertNotNull(appender.last);
        assertSame(ex, appender.last.getThrowable());
        assertEquals("failed", appender.last.getFormattedMessage());
    }

    /**
     * 测试 varargs 最后一个参数为 Throwable 时自动剥离为关联异常。
     * <p>剥离后该 Throwable 不再作为占位符填充，消息本身不含异常文本，
     * 异常通过 {@link LogEvent#getThrowable()} 获取。</p>
     */
    @Test
    void testThrowableDetectedFromVarargs() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
        CapturingAppender appender = new CapturingAppender();
        ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
        Logger log = LoggerFactory.getLogger("thVarargs");
        RuntimeException ex = new RuntimeException("kaboom");
        log.error("failed: {}", ex);
        assertNotNull(appender.last);
        assertSame(ex, appender.last.getThrowable());
        assertEquals("kaboom", ex.getMessage());
        assertTrue(appender.last.getFormattedMessage().contains("failed:"));
    }

    // ==================== 布局转换符 ====================

    /**
     * 测试 %ex 输出完整堆栈、%ex{short} 仅输出首行。
     */
    @Test
    void testLayoutThrowableConverter() {
        Throwable t = new IllegalStateException("bad state");
        LogEvent event = new LogEvent("t", Level.ERROR, "m", null, "m", t, null);

        Layout full = new Layout("%ex");
        String out = full.format(event);
        assertTrue(out.contains("IllegalStateException"), out);
        assertTrue(out.contains("at "), "full stack should contain frame lines: " + out);

        Layout shortFmt = new Layout("%ex{short}");
        String shortOut = shortFmt.format(event);
        assertTrue(shortOut.contains("IllegalStateException"), shortOut);
        assertFalse(shortOut.contains("\nat "), "short should not contain stack frames: " + shortOut);

        Layout none = new Layout("%m");
        LogEvent noTh = new LogEvent("t", Level.ERROR, "m", null, "m", null, null);
        assertEquals("m", none.format(noTh));
    }

    /**
     * 测试调用位置转换符 %C/%M/%L/%F 输出捕获到的位置，且 requiresCallerLocation 正确识别。
     */
    @Test
    void testLayoutCallerLocationConverter() {
        StackTraceElement loc = new StackTraceElement("com.example.Service", "process", "Service.java", 88);
        LogEvent event = new LogEvent("t", Level.INFO, "m", null, "m", null, loc);

        Layout layout = new Layout("%C %M %L %F");
        assertEquals("Service process 88 Service.java", layout.format(event));
        assertTrue(layout.requiresCallerLocation());

        Layout noLoc = new Layout("%level %msg");
        assertFalse(noLoc.requiresCallerLocation());
        LogEvent empty = new LogEvent("t", Level.INFO, "m", null, "m", null, null);
        assertEquals("? ? ? ?", new Layout("%C %M %L %F").format(empty));
    }

    /**
     * 测试 %r 输出非负相对时间（可解析为长整型）。
     */
    @Test
    void testLayoutRelativeTime() {
        LogEvent event = new LogEvent("t", Level.INFO, "m", null, "m", null, null);
        String out = new Layout("%r").format(event);
        long r = Long.parseLong(out.trim());
        assertTrue(r >= 0, "relative time should be non-negative: " + out);
    }

    /**
     * 测试 %highlight{...} 按级别包裹 ANSI 颜色，并包含内部消息与重置符。
     */
    @Test
    void testLayoutHighlight() {
        LogEvent event = new LogEvent("t", Level.INFO, "hello", null, "hello", null, null);
        String out = new Layout("%highlight{%m}").format(event);
        assertTrue(out.contains("hello"), out);
        assertTrue(out.contains("\u001B[0m"), "should contain ANSI reset: " + out);
        assertTrue(out.startsWith("\u001B["), "should start with ANSI color code: " + out);
    }

    // ==================== 滚动归档命名 filePattern ====================

    /**
     * 测试设置了 filePattern 后，大小滚动的归档文件名遵循模式（%i 序号）。
     */
    @Test
    void testRollingFilePatternNaming() throws IOException {
        try (VfsFileSystem fs = newMemFs()) {
            Path base = fs.getPath("/mem/app.log");
            Path archive = fs.getPath("/mem/app-1.log");

            RollingFileAppender appender = new RollingFileAppender(base);
            appender.setLayout(new Layout("%msg%n"));
            appender.setThreshold(Level.INFO);
            appender.policy(RollingPolicy.SIZE_BASED);
            appender.filePattern(fs.getPath("/mem/app-%i.log").toString());
            appender.maxSize(1); // 任意一行即触发滚动
            appender.maxHistory(3);

            ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.INFO);
            ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
            Logger log = LoggerFactory.getLogger("rollTest");
            log.info("first line");
            log.info("second line");
            appender.close();

            assertTrue(Files.exists(archive), "archive app-1.log should exist: " + archive);
            assertTrue(Files.exists(base), "current app.log should exist: " + base);
            String archivedContent = Files.readString(archive);
            assertTrue(archivedContent.contains("first line"), "archive should hold first line: " + archivedContent);
        }
    }

    // ==================== 惰性求值 Supplier ====================

    /**
     * 测试级别未启用时 Supplier 不被求值（避免无谓开销）。
     */
    @Test
    void testLazySupplierSkippedWhenDisabled() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.WARN);
        CapturingAppender appender = new CapturingAppender();
        ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
        AtomicBoolean evaluated = new AtomicBoolean(false);
        Logger log = LoggerFactory.getLogger("lazyTest");
        Supplier<String> neverRun = () -> {
            evaluated.set(true);
            return "expensive";
        };
        log.debug(neverRun);
        assertFalse(evaluated.get(), "Supplier must not be evaluated when DEBUG disabled");
        assertNull(appender.last);
    }

    /**
     * 测试级别启用时 Supplier 被求值并记录。
     */
    @Test
    void testLazySupplierEvaluatedWhenEnabled() {
        ((LoggerImpl) LoggerFactory.getRootLogger()).setLevel(Level.DEBUG);
        CapturingAppender appender = new CapturingAppender();
        ((LoggerImpl) LoggerFactory.getRootLogger()).addAppender(appender);
        Logger log = LoggerFactory.getLogger("lazyTest2");
        log.debug(() -> "lazy message");
        assertNotNull(appender.last);
        assertEquals("lazy message", appender.last.getFormattedMessage());
    }

    // ==================== 辅助类 ====================

    static class CountingAppender implements Appender {
        int count = 0;
        Layout layout = new Layout("%msg%n");
        Level threshold = Level.TRACE;
        String name;

        @Override public Level getThreshold() { return threshold; }
        @Override public void setThreshold(Level threshold) { this.threshold = threshold; }
        @Override public Layout getLayout() { return layout; }
        @Override public void setLayout(Layout layout) { this.layout = layout; }
        @Override public String getName() { return name; }
        @Override public void setName(String name) { this.name = name; }
        @Override public void append(LogEvent event) {
            if (threshold.isEnabled(event.getLevel())) {
                count++;
            }
        }
    }

    /**
     * 捕获最近一次 append 的日志事件，便于断言异常、级别、消息等字段。
     */
    static class CapturingAppender implements Appender {
        LogEvent last;
        Layout layout = new Layout("%msg%n");
        Level threshold = Level.TRACE;
        String name;

        @Override public Level getThreshold() { return threshold; }
        @Override public void setThreshold(Level threshold) { this.threshold = threshold; }
        @Override public Layout getLayout() { return layout; }
        @Override public void setLayout(Layout layout) { this.layout = layout; }
        @Override public String getName() { return name; }
        @Override public void setName(String name) { this.name = name; }
        @Override public void append(LogEvent event) {
            if (threshold.isEnabled(event.getLevel())) {
                last = event;
            }
        }
    }
}
