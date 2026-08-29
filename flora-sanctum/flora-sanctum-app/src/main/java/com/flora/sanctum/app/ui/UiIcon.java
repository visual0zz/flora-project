package com.flora.sanctum.app.ui;

/**
 * ui 图标注册表：枚举成员与 {@code /icons/ui/} 下的 SVG 文件一一对应。
 * <p>
 * 约束：新增/删除图标必须同步改本枚举——{@code UiIconTest} 会扫描资源目录
 * 断言"枚举与文件一个不多一个不少"。代码引用图标一律走 {@code SvgIcon.get(UiIcon, size)}，
 * 拼错枚举名在编译期即报错。
 */
public enum UiIcon {

    CHECK("ui/check"),
    CLOSE("ui/close"),
    DELETE("ui/delete"),
    ENTRY("ui/entry"),
    FOLDER("ui/folder"),
    ADD_IMAGE("ui/add-image"),
    ADD_KEY("ui/add-key"),
    LOCK("ui/lock"),
    NEW_ENTRY("ui/new-entry"),
    NEW_GROUP("ui/new-group"),
    ADD_REMOTE("ui/add-remote"),
    SETTINGS("ui/settings"),
    SYNC("ui/sync"),
    TRASH("ui/trash");

    private final String path;

    UiIcon(String path) {
        this.path = path;
    }

    /** 图标资源路径（{@code ui/xxx}，供 {@link SvgIcon#get(UiIcon, int)} 加载）。 */
    public String path() {
        return path;
    }

    /** 图标文件名（不含扩展名）。 */
    public String fileName() {
        return path.substring(path.indexOf('/') + 1);
    }
}
