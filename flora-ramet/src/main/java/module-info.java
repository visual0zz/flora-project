/**
 * ramet 基于模板的代码生成引擎模块。
 * <p>
 * 导出模板引擎公开 API 包 {@code com.flora.ramet}（模板函数与引擎门面），
 * 通过 {@link com.flora.ramet.TemplateFunction} SPI 支持自定义模板函数扩展。
 */
import com.flora.ramet.TemplateFunction;

module com.flora.ramet {
    requires com.flora.root;
    requires com.flora.shell;
    requires org.jetbrains.annotations;
    uses TemplateFunction;
    // 模板引擎公开 API（模板函数与引擎门面）
    exports com.flora.ramet;
}
