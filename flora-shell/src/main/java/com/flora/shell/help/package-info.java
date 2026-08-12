/**
 * 帮助聚合与渲染。
 * <p>命令类是 help 的唯一事实来源：{@code Command} 的声明层（name/description/args/usage）
 * 就是帮助数据。{@link com.flora.shell.help.HelpRenderer} 从一组命令聚合全局命令树并渲染文本。</p>
 */
package com.flora.shell.help;
