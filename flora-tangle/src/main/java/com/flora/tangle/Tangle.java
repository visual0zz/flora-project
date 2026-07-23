package com.flora.tangle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 命令行入口：把一个 jar 混淆成另一个 jar。
 *
 * <p>用法：<br>
 * {@code java -jar flora-tangle.jar <输入.jar> <输出.jar> [--keep <类前缀> ...]}
 *
 * <p>{@code --keep} 后可跟若干类内部名前缀（如 {@code com/foo/})，这些类不会被重命名，
 * 通常用于保留程序入口或需要被外部反射调用的类。
 */
public final class Tangle {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("用法: Tangle <输入.jar> <输出.jar> [--keep <类前缀> ...]");
            System.exit(2);
            return;
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);

        Obfuscator obf = new Obfuscator();
        for (int i = 2; i < args.length; i++) {
            if ("--keep".equals(args[i]) && i + 1 < args.length) {
                obf.keepClassPrefix(args[++i]);
            }
        }

        byte[] input = Files.readAllBytes(in);
        byte[] result = obf.obfuscate(input);
        Files.write(out, result);
        System.out.println("混淆完成: " + in + " -> " + out + " (" + result.length + " 字节)");
    }
}
