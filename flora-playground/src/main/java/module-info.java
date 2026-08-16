/**
 * flora-playground 模块定义文件。
 * <p>
 * 视觉/算法调参沙盒：渲染纸纤维噪声各分量与整体预览图，辅助调整
 * {@link com.flora.root.graphics.noise.PaperNoise} 参数。
 */
module com.flora.playground {
    exports com.flora.playground;

    requires com.flora.root;
    requires com.github.weisj.jsvg;
    requires java.desktop;
}
