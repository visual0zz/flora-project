package com.flora.root.graphics.noise;

/**
 * 确定性纸纤维噪声：多尺度 Perlin 合成（低频厚薄 + 各向异性纤维 + 高频白噪）。
 * <p>
 * 基于像素绝对坐标的确定性函数（无随机状态），任意尺寸渲染无缝；
 * 各分量可单独取值便于调试（见 {@code flora-playground} 分量预览图）。
 * 输出约 -1..1，幅度权重见各常量。
 */
public final class PaperNoise {

    private PaperNoise() {
    }

    /** 低频厚薄幅度权重（大面积明暗）。 */
    public static final float LOW_AMP = 0.45f;
    /** 中频纤维幅度权重（十字交叉走向）。 */
    public static final float MID_AMP = 0.2f;
    /** 高频白噪幅度权重（细微颗粒）。 */
    public static final float HIGH_AMP = 0.3f;

    /** 低频网格尺寸（像素）。 */
    public static final int LOW_CELL = 128;
    /** 中频纤维网格尺寸（像素）。 */
    public static final int MID_CELL = 32;
    /** 纤维各向异性拉伸因子（x 方向）。 */
    public static final float FIBER_SX = 1.6f;
    /** 纤维各向异性拉伸因子（y 方向）。 */
    public static final float FIBER_SY = 0.8f;

    /** 低频分量（纸张厚薄），约 -LOW_AMP..LOW_AMP。 */
    public static float low(int x, int y) {
        return perlinNoise(x, y, LOW_CELL) * LOW_AMP;
    }

    /** 中频分量（纵向/横向纤维十字 blend），约 -MID_AMP..MID_AMP。 */
    public static float mid(int x, int y) {
        float fx1 = perlinNoise(x * FIBER_SX, y * FIBER_SY, MID_CELL);
        float fx2 = perlinNoise(x * FIBER_SY, y * FIBER_SX, MID_CELL);
        return (fx1 * 0.6f + fx2 * 0.4f) * MID_AMP;
    }

    /** 高频颗粒扰动概率（约 1/4 的点有噪点）。 */
    public static final float HIGH_SPARSE = 0.25f;

    /** 高频分量（概率性白色颗粒，仅约 {@link #HIGH_SPARSE} 的点扰动），约 -HIGH_AMP..HIGH_AMP。 */
    public static float high(int x, int y) {
        // 独立 hash 通道决定是否扰动（与扰动值不相关，保证分布均匀）
        if (hash(x * 3 + 7, y * 5 + 11) < HIGH_SPARSE) {
            return whiteNoise(x, y) * HIGH_AMP;
        }
        return 0f;
    }

    /** 整体纸纤维噪声（低+中+高），约 -1..1。 */
    public static float paper(int x, int y) {
        return low(x, y) + mid(x, y) + high(x, y);
    }

    /** 确定性 2D Perlin 噪声（float 坐标 + 可调网格）：quintic fade + 梯度内插，输出约 -1..1。 */
    public static float perlinNoise(float x, float y, int cell) {
        int gx = (int) Math.floor(x / cell);
        int gy = (int) Math.floor(y / cell);
        float fx = (x - gx * cell) / (float) cell;
        float fy = (y - gy * cell) / (float) cell;
        float u = fade(fx);
        float v = fade(fy);
        float aa = grad(gx, gy, fx, fy);
        float ba = grad(gx + 1, gy, fx - 1, fy);
        float ab = grad(gx, gy + 1, fx, fy - 1);
        float bb = grad(gx + 1, gy + 1, fx - 1, fy - 1);
        float x1 = aa + u * (ba - aa);
        float x2 = ab + u * (bb - ab);
        return x1 + v * (x2 - x1);
    }

    /** 白噪声：-1..1，逐像素独立。 */
    public static float whiteNoise(int x, int y) {
        return hash(x, y) * 2f - 1f;
    }

    /** 字节值裁剪到 0..255。 */
    public static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static float grad(int ix, int iy, float fx, float fy) {
        int h = (int) (hash(ix, iy) * 0xFFFFFFL);
        switch (h & 3) {
            case 0: return fx + fy;
            case 1: return -fx + fy;
            case 2: return fx - fy;
            default: return -fx - fy;
        }
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float hash(int x, int y) {
        long h = x * 374761393L + y * 668265263L + 1103515245L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h = h ^ (h >>> 16);
        return (h & 0xFFFFFFL) / (float) 0xFFFFFFL;
    }
}
