package com.flora.ramet.idea;

import com.intellij.lang.Language;

/**
 * Ramet 模板语言的 IntelliJ Language 定义。
 *
 * <p>单例模式，Language ID = "Ramet"。
 */
public final class RametLanguage extends Language {

    public static final RametLanguage INSTANCE = new RametLanguage();

    private RametLanguage() {
        super("Ramet");
    }
}
