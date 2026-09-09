package com.flora.sanctum.app.ui;

import com.flora.sanctum.app.BackgroundExecutor;
import com.flora.sanctum.core.crypto.Argon2KDF;

import javax.swing.SwingUtilities;
import java.util.Locale;

/**
 * 「估算约 1 秒对应迭代数」的后台探针：设置页与新建仓库对话框共用。
 *
 * <p>两步实测（见 {@link #run}）：先跑迭代=1 得 t1；t1 &lt; 0.5s 时单次计时噪声大，
 * 先估出总耗时约 1 秒的迭代数 n0、实测「n0 次迭代」档总耗时并以 {@code T(n0)/n0}
 * 折算单次耗时；t1 ≥ 0.5s 则直接采用。回调统一在 EDT 上触发。</p>
 */
final class Argon2IterationProbe {

    /** 单次迭代实测低于该阈值（秒）时走两段折算（消除短时计时噪声）。 */
    private static final double REFINE_BELOW_SECONDS = 0.5;

    private Argon2IterationProbe() {
    }

    /** 估算完成/失败回调（均在 EDT 上触发）。 */
    interface Listener {
        /** @param perIterationSeconds 折算后的单次迭代耗时（秒） */
        void onDone(double perIterationSeconds);

        void onError(String message);
    }

    /**
     * 提交一次后台估算（{@link BackgroundExecutor} 串行执行，期间不判定自动锁定）。
     *
     * @param memoryKiB    当前输入的内存（KiB），须已通过 {@link Argon2KDF#validate}
     * @param parallelism  当前输入的并行度，须已通过 {@link Argon2KDF#validate}
     */
    static void run(BackgroundExecutor executor, int memoryKiB, int parallelism, Listener listener) {
        executor.submit(new BackgroundExecutor.Task() {
            @Override
            public String name() {
                return "测试 Argon2 耗时";
            }

            @Override
            public void run() {
                final double per;
                try {
                    double t1 = measureSeconds(memoryKiB, parallelism, 1);
                    if (t1 < REFINE_BELOW_SECONDS) {
                        // 单次太短、计时噪声大：先估出总耗时约 1 秒的迭代数 n0，
                        // 再实测该档总耗时，用 T(n0)/n0 折算单次耗时
                        int n0 = Argon2KDF.suggestIterationsForOneSecond(t1);
                        per = measureSeconds(memoryKiB, parallelism, n0) / n0;
                    } else {
                        per = t1;
                    }
                } catch (Exception ex) {
                    final String msg = ex.getMessage() == null
                            ? ex.getClass().getSimpleName() : ex.getMessage();
                    SwingUtilities.invokeLater(() -> listener.onError("测试失败：" + msg));
                    return;
                }
                final double result = per;
                SwingUtilities.invokeLater(() -> listener.onDone(result));
            }
        });
    }

    /** 括号提示文案：取使总耗时最接近 1 秒的整数迭代数 n，形如 {@code (n 次 ≈ x.xx 秒)}。 */
    static String hintText(double perIterationSeconds) {
        int n = Argon2KDF.suggestIterationsForOneSecond(perIterationSeconds);
        return "(" + n + " 次 ≈ " + String.format(Locale.ROOT, "%.2f", n * perIterationSeconds) + " 秒)";
    }

    /** 实测指定迭代数的总耗时（秒）：走与解锁完全相同的 Argon2KDF 派生路径。 */
    static double measureSeconds(int memoryKiB, int parallelism, int iterations) {
        byte[] salt = new byte[16];
        Argon2KDF kdf = new Argon2KDF(salt, memoryKiB, iterations, parallelism);
        char[] pwd = {'x'};
        long start = System.nanoTime();
        kdf.derive(pwd);
        return (System.nanoTime() - start) / 1_000_000_000.0;
    }
}
