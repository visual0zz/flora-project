package com.flora.tangle.testbed.tricky;

/**
 * 复杂循环和分支测试类，用于验证控制流混淆功能。
 * <p>
 * 包含嵌套 for 循环（3 层）、while 循环带 break/continue、
 * switch-case（含 fall-through）、三元运算符嵌套等结构，
 * 为混淆器提供丰富的控制流变换场景。
 * </p>
 */
public final class LoopTest {

    /**
     * 执行所有循环和分支操作并返回计算结果。
     *
     * @return 格式为 "LoopTest:sum=XX" 的结果字符串
     */
    public static String test() {
        int sum = 0;
        int count = 0;

        // ---- 1. 三层嵌套 for 循环 ----
        // 模拟矩阵求和: 遍历 4x5x3 的三维数组
        int[][][] matrix = new int[4][5][3];
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 3; z++) {
                    matrix[x][y][z] = (x + 1) * (y + 1) * (z + 1);
                    sum += matrix[x][y][z];
                    count++;
                }
            }
        }

        // ---- 2. while 循环带 break/continue ----
        int i = 0;
        int primeSum = 0;
        while (i < 50) {
            i++;
            if (i == 1) {
                continue; // 1 不是质数
            }
            if (i > 40) {
                break; // 超过 40 不再计算
            }
            boolean isPrime = true;
            for (int d = 2; d * d <= i; d++) {
                if (i % d == 0) {
                    isPrime = false;
                    break;
                }
            }
            if (isPrime) {
                primeSum += i;
            }
        }
        sum += primeSum;

        // ---- 3. switch-case（含 fall-through + break） ----
        int switchSum = 0;
        for (int v = 0; v < 12; v++) {
            switch (v % 4) {
                case 0:
                    switchSum += v;
                    // fall-through
                case 1:
                    switchSum += v * 2;
                    break;
                case 2:
                    switchSum += v * 3;
                    break;
                default:
                    switchSum += v;
                    break;
            }
        }
        sum += switchSum;

        // ---- 4. 三元运算符嵌套 ----
        int ternarySum = 0;
        for (int v = -5; v <= 5; v++) {
            int mapped = (v > 0)
                    ? (v % 2 == 0 ? v * 10 : v * 5)
                    : (v == 0 ? 0 : v * (-3));
            ternarySum += mapped;
        }
        sum += ternarySum;

        // ---- 5. do-while 循环 ----
        int doSum = 0;
        int d = 0;
        do {
            if (d % 3 == 0 && d % 5 == 0) {
                doSum += d; // 15 的倍数
            }
            d++;
        } while (d <= 60);
        sum += doSum;

        // ---- 6. 带标签的 break（跳出外层循环） ----
        int labelSum = 0;
        outer:
        for (int a = 1; a <= 10; a++) {
            for (int b = 1; b <= 10; b++) {
                if (a * b > 50) {
                    break outer;
                }
                labelSum += a * b;
            }
        }
        sum += labelSum;

        // ---- 7. 增强 for 循环 + 条件过滤 ----
        int[] data = {3, 7, 1, 9, 4, 6, 8, 2, 5, 0, 11, 13, 17, 19, 23};
        int filteredSum = 0;
        int oddCount = 0;
        for (int n : data) {
            if (n % 2 == 0) {
                continue; // 跳过偶数
            }
            filteredSum += n;
            oddCount++;
        }
        sum += filteredSum;

        return "LoopTest:sum=" + sum + ",迭代次数=" + count + ",质数和=" + primeSum
                + ",switch和=" + switchSum + ",三元和=" + ternarySum;
    }
}
