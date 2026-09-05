package com.flora.sanctum.app.ui;

import com.flora.sanctum.app.config.UserConfig;
import com.flora.sanctum.core.model.LibraryConfig;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.nio.file.Path;

/**
 * 设置页的纯模型层：把「一条设置」建模为自描述对象，使类型信息留在 model 中。
 *
 * <p>路由：{@link Setting#store()} 声明该值落在哪套存储（{@link SettingStore#USER} 明文全局配置，
 * 或 {@link SettingStore#VAULT} 仓库内加密配置），由 {@link Setting#createControl}/{@link Setting#saveValue}
 * 直接读写对应后端。键名、默认值、校验、控件均集中在各 {@link Setting} 实现内一处定义。</p>
 */
final class SettingsModel {

    /** 值所在的存储后端。 */
    enum SettingStore { USER, VAULT }

    /** 右栏编辑控件种类。 */
    enum Widget { COMBO, INT_FIELD, ACTION }

    /** 设置读写上下文：持有两套配置后端与独立运行操作的回调。 */
    static final class SettingsContext {
        final UserConfig user;
        final LibraryConfig vault;
        final Path repoRoot;
        final boolean standalone;
        final boolean selfLaunched;
        final Runnable upgradeStandalone;
        final Runnable downgradeStandalone;
        final Runnable refreshStandaloneRuntime;

        SettingsContext(UserConfig user, LibraryConfig vault, Path repoRoot, boolean standalone,
                        boolean selfLaunched, Runnable upgradeStandalone,
                        Runnable downgradeStandalone, Runnable refreshStandaloneRuntime) {
            this.user = user;
            this.vault = vault;
            this.repoRoot = repoRoot;
            this.standalone = standalone;
            this.selfLaunched = selfLaunched;
            this.upgradeStandalone = upgradeStandalone;
            this.downgradeStandalone = downgradeStandalone;
            this.refreshStandaloneRuntime = refreshStandaloneRuntime;
        }

        UserConfig user() {
            return user;
        }

        LibraryConfig vault() {
            return vault;
        }

        Path repoRoot() {
            return repoRoot;
        }

        boolean standalone() {
            return standalone;
        }

        /** 当前进程是否正从该仓库自带的 lib/ 启动（运行时改动 lib/ 会破坏自己）。 */
        boolean selfLaunched() {
            return selfLaunched;
        }

        Runnable upgradeStandalone() {
            return upgradeStandalone;
        }

        Runnable downgradeStandalone() {
            return downgradeStandalone;
        }

        Runnable refreshStandaloneRuntime() {
            return refreshStandaloneRuntime;
        }
    }

    /** 左栏区段（同时携带显示名与种类）。 */
    static final class SettingsCategory {
        enum Kind { GLOBAL, VAULT, ICON, SSH_KEY, REMOTE }

        private final Kind kind;
        private final String label;

        SettingsCategory(Kind kind, String label) {
            this.kind = kind;
            this.label = label;
        }

        Kind kind() {
            return kind;
        }

        String label() {
            return label;
        }
    }

    /** 中栏条目：要么是键值设置（{@link SettingEntry}），要么是仓库对象（{@link ObjectEntry}）。 */
    sealed interface SettingsEntry permits SettingEntry, ObjectEntry {
        String label();
    }

    /** 键值设置条目。 */
    final record SettingEntry(Setting setting) implements SettingsEntry {
        @Override
        public String label() {
            return setting.label();
        }
    }

    /** 仓库对象条目（图标 / SSH 密钥 / 远程），携带 id 与所属种类。 */
    final record ObjectEntry(String id, String label, SettingsCategory.Kind kind) implements SettingsEntry {
    }

    /** 一条键值设置：自描述键名、显示名、控件种类、所属存储，并负责自身控件的创建与读写。 */
    interface Setting {
        String key();

        String label();

        Widget widget();

        SettingStore store();

        /** 构建预填值的编辑控件。 */
        JComponent createControl(SettingsContext ctx);

        /**
         * 从控件读取值并写入对应存储。
         *
         * @return 错误信息；{@code null} 表示保存成功（调用方据此提示并中断）。
         */
        String saveValue(SettingsContext ctx, JComponent control);
    }

    /** 界面主题：仅存全局明文配置（UserConfig），不写仓库加密配置。 */
    enum ThemeSetting implements Setting {
        INSTANCE;

        @Override public String key() {
            return "theme";
        }

        @Override public String label() {
            return "界面主题";
        }

        @Override public Widget widget() {
            return Widget.COMBO;
        }

        @Override public SettingStore store() {
            return SettingStore.USER;
        }

        @Override
        public JComponent createControl(SettingsContext ctx) {
            JComboBox<String> combo = new JComboBox<>(new String[]{"light", "dark", "stupid"});
            combo.setSelectedItem(ctx.user().theme());
            combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            return combo;
        }

        @Override
        public String saveValue(SettingsContext ctx, JComponent control) {
            ctx.user().setTheme((String) ((JComboBox<?>) control).getSelectedItem());
            return null;
        }
    }

    /** 自动锁定时长（秒）：存仓库加密配置。 */
    enum LockTimeoutSetting implements Setting {
        INSTANCE;

        @Override public String key() {
            return "lockTimeoutSeconds";
        }

        @Override public String label() {
            return "自动锁定（秒）";
        }

        @Override public Widget widget() {
            return Widget.INT_FIELD;
        }

        @Override public SettingStore store() {
            return SettingStore.VAULT;
        }

        @Override
        public JComponent createControl(SettingsContext ctx) {
            JTextField f = new JTextField(String.valueOf(ctx.vault().lockTimeoutSeconds()));
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            return f;
        }

        @Override
        public String saveValue(SettingsContext ctx, JComponent control) {
            try {
                ctx.vault().setLockTimeoutSeconds(Integer.parseInt(((JTextField) control).getText()));
                return null;
            } catch (NumberFormatException e) {
                return "自动锁定须为整数秒";
            } catch (IllegalArgumentException e) {
                return "自动锁定须为非负整数秒";
            }
        }
    }

    /** 剪贴板清空时长（秒）：存仓库加密配置。 */
    enum ClipboardClearSetting implements Setting {
        INSTANCE;

        @Override public String key() {
            return "clipboardClearSeconds";
        }

        @Override public String label() {
            return "剪贴板清空（秒）";
        }

        @Override public Widget widget() {
            return Widget.INT_FIELD;
        }

        @Override public SettingStore store() {
            return SettingStore.VAULT;
        }

        @Override
        public JComponent createControl(SettingsContext ctx) {
            JTextField f = new JTextField(String.valueOf(ctx.vault().clipboardClearSeconds()));
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            return f;
        }

        @Override
        public String saveValue(SettingsContext ctx, JComponent control) {
            try {
                ctx.vault().setClipboardClearSeconds(Integer.parseInt(((JTextField) control).getText()));
                return null;
            } catch (NumberFormatException e) {
                return "剪贴板清空须为整数秒";
            } catch (IllegalArgumentException e) {
                return "剪贴板清空须为非负整数秒";
            }
        }
    }

    /** 独立运行形态：展示当前形态并提供 配置/删除 操作（无键值，保存为 no-op）。 */
    enum StandaloneSetting implements Setting {
        INSTANCE;

        @Override public String key() {
            return "standalone";
        }

        @Override public String label() {
            return "运行形态";
        }

        @Override public Widget widget() {
            return Widget.ACTION;
        }

        @Override public SettingStore store() {
            return SettingStore.VAULT;
        }

        @Override
        public JComponent createControl(SettingsContext ctx) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setOpaque(false);
            JLabel state = new JLabel(ctx.standalone()
                    ? "当前：独立运行（自带 lib/ 与 edit 脚本）"
                    : "当前：普通仓库（依赖本应用打开）");
            state.setHorizontalAlignment(SwingConstants.LEFT);
            p.add(state);
            p.add(Box.createVerticalStrut(10));
            if (ctx.selfLaunched()) {
                // 当前进程正从该仓库自带的 lib/ 启动：运行时改动 lib/ 会破坏正在运行的应用，
                // 故不提供删除/更新等自我保护冲突的操作，仅提示。
                JLabel note = new JLabel(
                        "<html><body style='width:260px'>当前应用正从该独立仓启动，为避免运行时破坏自身，"
                                + "此处不提供删除/更新操作。请改用其它方式启动本应用后再操作。</body></html>");
                note.setHorizontalAlignment(SwingConstants.LEFT);
                p.add(note);
            } else if (ctx.standalone()) {
                // 打开的是独立仓、但本应用并非从它启动：提供删除与更新运行时版本
                JButton del = new JButton("删除独立运行");
                del.setHorizontalAlignment(SwingConstants.LEFT);
                del.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                del.addActionListener(e -> ctx.downgradeStandalone().run());
                p.add(del);
                p.add(Box.createVerticalStrut(6));
                JButton upd = new JButton("更新运行时版本");
                upd.setHorizontalAlignment(SwingConstants.LEFT);
                upd.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                upd.addActionListener(e -> ctx.refreshStandaloneRuntime().run());
                p.add(upd);
            } else {
                JButton b = new JButton("配置独立运行");
                b.setHorizontalAlignment(SwingConstants.LEFT);
                b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                b.addActionListener(e -> ctx.upgradeStandalone().run());
                p.add(b);
            }
            return p;
        }

        @Override
        public String saveValue(SettingsContext ctx, JComponent control) {
            return null;
        }
    }

    private SettingsModel() {
    }
}
