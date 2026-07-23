package com.flora.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * 日志框架的完整单元测试。
 * 测试 Logger 工厂、消息格式化、布局格式、日志级别、Appender（控制台/文件）、级别继承、可加性、配置 API、参数化日志、MDC 及无配置场景。
 */
class LogTest {

    @AfterEach
    void tearDown() {
        LoggerFactory.reset();
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
        assertEquals(Level.DEBUG, LoggerFactory.getRootLogger().getEffectiveLevel());
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
        LoggerFactory.getRootLogger().setLevel(Level.WARN);
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
        LoggerFactory.getRootLogger().setLevel(Level.INFO);
        ConsoleAppender appender = new ConsoleAppender();
        appender.setLayout(new Layout("%level %msg%n"));
        LoggerFactory.getRootLogger().addAppender(appender);
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
        LoggerFactory.reset();
        Path tmpFile = Files.createTempFile("log-test-", ".log");
        tmpFile.toFile().deleteOnExit();

        LoggerFactory.getRootLogger().setLevel(Level.INFO);
        FileAppender appender = new FileAppender(tmpFile.toString());
        appender.setLayout(new Layout("%msg%n"));
        LoggerFactory.getRootLogger().addAppender(appender);

        Logger log = LoggerFactory.getLogger("fileTest");
        log.info("hello file");
        log.info("second line");
        appender.close();

        String content = Files.readString(tmpFile);
        assertTrue(content.contains("hello file"), "content=[" + content + "]");
        assertTrue(content.contains("second line"), "content=[" + content + "]");
    }

    // ==================== 级别继承 ====================

    /**
     * 测试子 Logger 从父 Logger 继承级别。
     */
    @Test
    void testLevelInheritance() {
        LoggerFactory.getRootLogger().setLevel(Level.INFO);
        Logger child = LoggerFactory.getLogger("com.example");
        assertEquals(Level.INFO, ((LoggerImpl) child).getEffectiveLevel());
        assertTrue(child.isInfoEnabled());

        LoggerFactory.getRootLogger().setLevel(Level.DEBUG);
        assertEquals(Level.DEBUG, ((LoggerImpl) child).getEffectiveLevel());
    }

    /**
     * 测试子 Logger 覆盖父 Logger 的级别。
     */
    @Test
    void testChildOverridesLevel() {
        LoggerFactory.getRootLogger().setLevel(Level.INFO);
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
        LoggerFactory.getRootLogger().setLevel(Level.INFO);

        CountingAppender rootAppender = new CountingAppender();
        LoggerFactory.getRootLogger().addAppender(rootAppender);

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
        LoggerFactory.reset();
        Path tmpFile = Files.createTempFile("log-config-", ".log");
        tmpFile.toFile().deleteOnExit();

        LogConfig.configure(cfg -> cfg
                .rootLevel(Level.INFO)
                .fileAppender(f -> f
                        .file(tmpFile.toString())
                        .pattern("%msg%n")));

        Logger log = LoggerFactory.getLogger("configTest");
        log.info("config file test");
        log.info("second");
        LoggerFactory.getRootLogger().getAppenders().forEach(Appender::close);

        String content = Files.readString(tmpFile);
        assertTrue(content.contains("config file test"), "content=[" + content + "]");
    }

    /**
     * 测试通过 LogConfig 配置级别路由（不同级别输出到不同文件）。
     */
    @Test
    void testLogConfigLevelRouting() throws IOException {
        LoggerFactory.reset();
        Path errorFile = Files.createTempFile("log-error-", ".log");
        errorFile.toFile().deleteOnExit();
        Path infoFile = Files.createTempFile("log-info-", ".log");
        infoFile.toFile().deleteOnExit();

        LogConfig.configure(cfg -> cfg
                .rootLevel(Level.INFO)
                .fileAppender(f -> f
                        .file(infoFile.toString())
                        .pattern("%msg%n"))
                .levelRouting(Level.ERROR, rt -> rt
                        .file(errorFile.toString())
                        .pattern("%msg%n")));

        Logger log = LoggerFactory.getLogger("routingTest");
        log.info("info msg");
        log.error("error msg");
        LoggerFactory.getRootLogger().getAppenders().forEach(Appender::close);

        String infoContent = Files.readString(infoFile);
        String errorContent = Files.readString(errorFile);

        assertTrue(infoContent.contains("info msg"), "infoContent=[" + infoContent + "]");
        assertTrue(infoContent.contains("error msg"), "infoContent should have error msg too");
        assertTrue(errorContent.contains("error msg"), "errorContent=[" + errorContent + "]");
        assertFalse(errorContent.contains("info msg"), "errorContent should NOT have info msg");
    }

    // ==================== 参数化日志 ====================

    /**
     * 测试参数化日志输出（占位符替换）。
     */
    @Test
    void testParameterizedLogging() {
        LoggerFactory.getRootLogger().setLevel(Level.INFO);
        CountingAppender appender = new CountingAppender();
        LoggerFactory.getRootLogger().addAppender(appender);
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
}
