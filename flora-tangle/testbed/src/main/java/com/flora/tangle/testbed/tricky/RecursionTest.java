package com.flora.tangle.testbed.tricky;

/**
 * 递归方法和尾递归测试类，验证混淆器对递归调用的处理。
 * <p>
 * 包含简单递归（阶乘）、相互递归（方法A调B，B调A）、
 * 递归深度控制、斐波那契数列等场景，确保混淆不会破坏
 * 递归调用栈的逻辑。
 * </p>
 */
public final class RecursionTest {

    /** 递归深度上限 */
    private static final int MAX_DEPTH = 20;

    /**
     * 简单递归 - 计算阶乘（演示基本递归结构）。
     *
     * @param n 非负整数
     * @return n 的阶乘
     * @throws IllegalArgumentException 如果 n 为负数
     */
    public static long factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("阶乘参数不能为负数: " + n);
        }
        if (n <= 1) {
            return 1;
        }
        return n * factorial(n - 1);
    }

    /**
     * 尾递归风格的阶乘（使用累加器）。
     *
     * @param n     非负整数
     * @param acc   累加器
     * @return n 的阶乘
     */
    private static long factorialTail(int n, long acc) {
        if (n <= 1) {
            return acc;
        }
        return factorialTail(n - 1, acc * n);
    }

    /**
     * 尾递归入口。
     *
     * @param n 非负整数
     * @return n 的阶乘
     */
    public static long factorialTail(int n) {
        return factorialTail(n, 1);
    }

    /**
     * 相互递归对 - 判断奇偶性（方法A）。
     *
     * @param n 非负整数
     * @return 如果是奇数返回 true
     */
    public static boolean isOdd(int n) {
        if (n == 0) {
            return false;
        }
        return isEven(n - 1);
    }

    /**
     * 相互递归对 - 判断奇偶性（方法B）。
     *
     * @param n 非负整数
     * @return 如果是偶数返回 true
     */
    public static boolean isEven(int n) {
        if (n == 0) {
            return true;
        }
        return isOdd(n - 1);
    }

    /**
     * 互相递归对 - 阿克曼函数。
     * 经典的双递归嵌套，对混淆器是很好的测试。
     *
     * @param m 非负整数
     * @param n 非负整数
     * @return 阿克曼函数值
     */
    public static long ackermann(int m, long n) {
        if (m == 0) {
            return n + 1;
        }
        if (n == 0) {
            return ackermann(m - 1, 1);
        }
        return ackermann(m - 1, ackermann(m, n - 1));
    }

    /**
     * 带深度控制的递归 - 在达到深度上限时切换策略。
     *
     * @param n     当前值
     * @param depth 当前深度
     * @param limit 深度上限
     * @return 计算结果
     */
    public static long depthControlledRecursion(long n, int depth, int limit) {
        if (depth >= limit) {
            // 超过深度上限，改用迭代
            long result = 0;
            for (long i = 0; i <= n; i++) {
                result += i;
            }
            return result;
        }
        if (n <= 0) {
            return 0;
        }
        return n + depthControlledRecursion(n - 1, depth + 1, limit);
    }

    /**
     * 多分支递归 - 计算所有路径和。
     *
     * @param n 分支参数
     * @return 路径和
     */
    public static long multiBranchRecursion(int n) {
        if (n <= 0) {
            return 1;
        }
        long sum = 0;
        // 分支 A
        sum += multiBranchRecursion(n - 1);
        if (n > 2) {
            // 分支 B（仅在 n > 2 时触发）
            sum += multiBranchRecursion(n - 2);
        }
        // 分支 C
        sum += n;
        return sum;
    }

    /**
     * 执行所有递归操作并返回结果摘要。
     *
     * @return 格式为 "RecursionTest:OK:xxx" 的结果字符串
     */
    public static String test() {
        StringBuilder result = new StringBuilder();

        // ---- 1. 简单递归: 阶乘 ----
        long fact5 = factorial(5);
        long fact10 = factorial(10);
        result.append("fact(5)=").append(fact5)
                .append(", fact(10)=").append(fact10);

        // ---- 2. 尾递归阶乘 ----
        long factTail10 = factorialTail(10);
        long factTail15 = factorialTail(15);
        result.append(" | 尾递归: factTail(10)=").append(factTail10)
                .append(", factTail(15)=").append(factTail15);

        // ---- 3. 相互递归: 奇偶判断 ----
        boolean odd7 = isOdd(7);
        boolean even8 = isEven(8);
        boolean odd12 = isOdd(12);
        result.append(" | 相互递归: isOdd(7)=").append(odd7)
                .append(", isEven(8)=").append(even8)
                .append(", isOdd(12)=").append(odd12);

        // ---- 4. 相互递归: 阿克曼函数 ----
        long ack2_1 = ackermann(2, 1);
        long ack2_2 = ackermann(2, 2);
        long ack3_1 = ackermann(3, 1);
        result.append(" | 阿克曼: A(2,1)=").append(ack2_1)
                .append(", A(2,2)=").append(ack2_2)
                .append(", A(3,1)=").append(ack3_1);

        // ---- 5. 深度控制的递归 ----
        long dcResult = depthControlledRecursion(10, 0, MAX_DEPTH);
        long dcWithLimit = depthControlledRecursion(100, 0, 5); // 超出深度上限
        result.append(" | 深度控制: norm=").append(dcResult)
                .append(", limit_hit=").append(dcWithLimit);

        // ---- 6. 多分支递归 ----
        long mb3 = multiBranchRecursion(3);
        long mb5 = multiBranchRecursion(5);
        result.append(" | 多分支: mb(3)=").append(mb3)
                .append(", mb(5)=").append(mb5);

        // ---- 7. 阶乘零值/边界 ----
        long fact0 = factorial(0);
        long fact1 = factorial(1);
        result.append(" | 边界: fact(0)=").append(fact0)
                .append(", fact(1)=").append(fact1);

        // ---- 8. 斐波那契（递归实现，验证更多递归结构）----
        long fib10 = fibonacci(10);
        long fib15 = fibonacci(15);
        result.append(" | 斐波那契: fib(10)=").append(fib10)
                .append(", fib(15)=").append(fib15);

        return "RecursionTest:OK:" + result.toString();
    }

    /**
     * 递归计算斐波那契数列（树形递归，测试重复递归路径）。
     *
     * @param n 位置
     * @return 第 n 个斐波那契数
     */
    public static long fibonacci(int n) {
        if (n <= 0) {
            return 0;
        }
        if (n == 1) {
            return 1;
        }
        return fibonacci(n - 1) + fibonacci(n - 2);
    }
}
