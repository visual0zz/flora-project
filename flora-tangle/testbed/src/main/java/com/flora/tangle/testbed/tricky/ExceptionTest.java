package com.flora.tangle.testbed.tricky;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * 异常处理链测试类，验证混淆不破坏异常表。
 * <p>
 * 包含多层 try-catch-finally（3 层嵌套）、自定义异常类、
 * try-with-resources、checked exception 抛出等场景，
 * 确保混淆后的代码异常表结构正确。
 * </p>
 */
public final class ExceptionTest {

    /**
     * 自定义业务异常 - 验证异常场景。
     * 包含错误码和错误详情字段。
     */
    public static class BusinessException extends Exception {
        private final int errorCode;

        public BusinessException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public BusinessException(int errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

    /**
     * 自定义运行时异常 - 验证非受检异常。
     */
    public static class ValidationException extends RuntimeException {
        private final String field;

        public ValidationException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    /**
     * 自定义资源异常 - 用于 try-with-resources 测试。
     */
    public static class ResourceException extends Exception {
        public ResourceException(String message) {
            super(message);
        }
    }

    /**
     * 实现 AutoCloseable 的自定义资源类，用于 try-with-resources 测试。
     */
    public static class TestResource implements AutoCloseable {
        private final String name;
        private boolean closed = false;

        public TestResource(String name) {
            this.name = name;
        }

        public String read() throws ResourceException {
            if ("broken".equals(name)) {
                throw new ResourceException("资源 " + name + " 读取失败");
            }
            return "来自资源 " + name + " 的数据";
        }

        @Override
        public void close() {
            this.closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    /**
     * 第一层异常处理：捕获并包装异常。
     *
     * @param flag 控制是否触发异常
     * @return 处理结果描述
     * @throws BusinessException 包装后的业务异常
     */
    private static String level1(boolean flag) throws BusinessException {
        try {
            String result = level2(flag);
            return "第一层成功: " + result;
        } catch (ValidationException e) {
            throw new BusinessException(100, "第一层捕获到验证异常: " + e.getField(), e);
        } catch (RuntimeException e) {
            throw new BusinessException(101, "第一层捕获到运行时异常", e);
        } finally {
            // 即使有异常也保证执行
            @SuppressWarnings("unused")
            String finallyMsg = "第一层 finally 执行";
        }
    }

    /**
     * 第二层异常处理：嵌套 try-catch-finally。
     *
     * @param flag 控制是否触发异常
     * @return 处理结果描述
     * @throws ValidationException 验证异常
     */
    private static String level2(boolean flag) throws ValidationException {
        try {
            String result = level3(flag);
            return "第二层成功: " + result;
        } catch (IllegalArgumentException e) {
            // 捕获后重新包装
            throw new ValidationException("param", "参数异常: " + e.getMessage());
        } finally {
            cleanupSecondLevel();
        }
    }

    /**
     * 第二层 finally 辅助方法。
     */
    private static void cleanupSecondLevel() {
        @SuppressWarnings("unused")
        String cleanMsg = "第二层资源清理完成";
    }

    /**
     * 第三层异常处理：最内层逻辑，触发实际的异常源。
     *
     * @param flag true 抛出异常，false 正常返回
     * @return 结果字符串
     */
    private static String level3(boolean flag) {
        try {
            if (flag) {
                throw new IllegalArgumentException("第三层: 参数 flag 为 true，触发异常");
            }
            // try-with-resources
            try (TestResource res1 = new TestResource("资源A");
                 TestResource res2 = new TestResource("资源B")) {
                String data1 = res1.read();
                String data2 = res2.read();
                return data1 + " + " + data2;
            } catch (ResourceException e) {
                throw new RuntimeException("资源操作异常", e);
            }
        } finally {
            @SuppressWarnings("unused")
            String innerFinally = "第三层 finally 执行";
        }
    }

    /**
     * 执行多层异常处理并返回过程摘要。
     *
     * @return 格式为 "ExceptionTest:OK:xxx" 的结果字符串
     */
    public static String test() {
        StringBuilder summary = new StringBuilder();

        // ---- 场景 1: 触发异常链 ----
        try {
            String result = level1(true);
            summary.append("异常场景不应到达此处");
        } catch (BusinessException e) {
            summary.append("捕获BusinessException: code=").append(e.getErrorCode())
                    .append(", msg=").append(e.getMessage());
        } finally {
            summary.append(" | 场景1完成");
        }

        // ---- 场景 2: 正常路径 ----
        try {
            String result = level1(false);
            summary.append(" | 正常路径: ").append(result);
        } catch (BusinessException e) {
            summary.append(" | 正常路径不应抛出异常");
        }

        // ---- 场景 3: try-with-resources 验证 ----
        try (TestResource res = new TestResource("normal")) {
            String data = res.read();
            summary.append(" | try-with-resources: ").append(data);
        } catch (ResourceException e) {
            summary.append(" | try-with-resources异常: ").append(e.getMessage());
        }

        // ---- 场景 4: try-with-resources 失败路径 ----
        try (TestResource res = new TestResource("broken")) {
            String data = res.read();
            summary.append(" | 不应到达");
        } catch (ResourceException e) {
            summary.append(" | 捕获资源异常: ").append(e.getMessage());
        }

        // ---- 场景 5: IOException 类异常 ----
        try {
            BufferedReader reader = new BufferedReader(new StringReader("测试数据\n第二行"));
            String line1 = reader.readLine();
            String line2 = reader.readLine();
            summary.append(" | IO读取: ").append(line1).append(",").append(line2);
            reader.close();
        } catch (IOException e) {
            summary.append(" | IO异常: ").append(e.getMessage());
        }

        return "ExceptionTest:OK:" + summary.toString();
    }
}
