/**
 * garden 占位模块。
 * <p>
 * 当前仅传递依赖 flora-root，作为项目占位/聚合模块，暂不导出任何包。
 */
module com.flora.garden {
    requires transitive com.flora.root;
}
