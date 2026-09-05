package com.flora.sanctum.app.ui;

/**
 * ui 图标注册表：枚举成员与 {@code /icon/button/} 或 {@code /icon/item/} 下的 SVG 文件一一对应。
 * <p>
 * 约束：新增/删除图标必须同步改本枚举——{@code UiIconTest} 会扫描这两个资源目录
 * 断言"枚举与文件一个不多一个不少"。代码引用图标一律走 {@code SvgIcon.get(UiIcon, size)}，
 * 拼错枚举名在编译期即报错。</p>
 * <p>
 * 归类：{@code item/} 放代表"事物/类型"的图标（条目、文件夹）；{@code button/} 放画在按钮上的操作图标。</p>
 */
public enum UiIcon {

    GO_BACK("button/go-back"),
    DELETE_ITEM("button/delete-item"),
    ADD_ICON("button/add-icon"),
    ADD_SSH_KEY("button/add-ssh-key"),
    LOCK_VAULT("button/lock-vault"),
    NEW_ENTRY("button/new-entry"),
    NEW_GROUP("button/new-group"),
    ADD_REMOTE("button/add-remote"),
    SETTINGS("button/settings"),
    SYNC_NOW("button/sync-now"),
    TRASH("button/trash"),
    IMPORT("button/import"),
    EXPORT("button/export"),
    EYE("button/eye"),
    EYE_OFF("button/eye-off"),
    ENTRY("item/entry"),
    FOLDER("item/folder");

    private final String path;

    UiIcon(String path) {
        this.path = path;
    }

    /** 图标资源路径（{@code button/xxx} 或 {@code item/xxx}，供 {@link SvgIcon#get(UiIcon, int)} 加载）。 */
    public String path() {
        return path;
    }

    /** 图标文件名（不含扩展名）。 */
    public String fileName() {
        return path.substring(path.indexOf('/') + 1);
    }
}
