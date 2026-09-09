package com.flora.root.container.order;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.flora.root.container.order.RocicorpFractionalIndexTest.assertInsertOK;
import static com.flora.root.container.order.RocicorpFractionalIndexTest.logOp;
import static com.flora.root.container.order.RocicorpFractionalIndexTest.verifyGlobal;

/**
 * {@link RocicorpFractionalIndex} 的大步数随机压测（slow）。
 *
 * <p>逻辑与 {@link RocicorpFractionalIndexTest#modelBasedRandomInsertMoveDelete()} 完全一致，
 * 仅把步数放大到 400k 以在更长时间内持续冲击实现，发现低概率边界问题。
 * 标记为 slow，避免拖慢常规测试；由 test-slow.cmd 覆盖执行。</p>
 *
 * <p>每次运行使用随机种子；可用 {@code -DfractionalIndex.stress.seed=<long>} 固定种子复现失败现场。
 * 失败时断言信息会附带 seed、步号与近期操作历史，便于定位。</p>
 */
@Tag("slow")
class RocicorpFractionalIndexStressSlowTest {

    @Test
    void modelBasedRandomInsertMoveDelete400k() {
        // 随机种子执行；-DfractionalIndex.stress.seed=<long> 可锁定同一种子复现
        String seedProp = System.getProperty("fractionalIndex.stress.seed");
        final long seed = seedProp != null ? Long.parseLong(seedProp) : new Random().nextLong();
        System.out.println("[RocicorpFractionalIndexStress@slow] 本次随机种子 seed=" + seed);
        final int steps = 400_000;
        final RocicorpFractionalIndex f = RocicorpFractionalIndex.INSTANCE;
        Random rnd = new Random(seed);

        List<String> keys = new ArrayList<>();      // 按 compareTo 升序，即被测试的“真实”列表
        keys.add(f.first());
        List<String> recent = new ArrayList<>();     // 近期操作历史（滚动保留，用于回溯）

        for (int step = 1; step <= steps; step++) {
            int op = rnd.nextInt(3); // 0=插入 1=拖动 2=删除
            if (op == 0 || keys.size() < 2) {
                // 插入：在随机位置 p 生成 between(prev, next) 并放入
                int p = rnd.nextInt(keys.size() + 1);
                String prev = p == 0 ? null : keys.get(p - 1);
                String next = p == keys.size() ? null : keys.get(p);
                String newKey = f.between(prev, next);
                assertInsertOK(f, keys, p, prev, next, newKey, step, seed, recent, "INSERT");
                keys.add(p, newKey);
                logOp(recent, step, "INSERT @" + p + " prev=" + prev + " next=" + next + " -> " + newKey);
            } else if (op == 1) {
                // 拖动：把 from 处元素移到 to 处（先移除再按新邻居重新生成键）
                int from = rnd.nextInt(keys.size());
                String moved = keys.remove(from);
                int to = rnd.nextInt(keys.size() + 1);
                String prev = to == 0 ? null : keys.get(to - 1);
                String next = to == keys.size() ? null : keys.get(to);
                String newKey = f.between(prev, next);
                assertInsertOK(f, keys, to, prev, next, newKey, step, seed, recent,
                        "MOVE(" + moved + " from=" + from + " to=" + to + ")");
                keys.add(to, newKey);
                logOp(recent, step, "MOVE " + moved + " from=" + from + " to=" + to
                        + " prev=" + prev + " next=" + next + " -> " + newKey);
            } else {
                // 删除
                int at = rnd.nextInt(keys.size());
                String removed = keys.remove(at);
                logOp(recent, step, "DELETE @" + at + " " + removed);
            }
            // 每步后做整体校验（全序 + 合法 + 唯一）
            verifyGlobal(f, keys, step, seed, recent);
        }
    }
}
