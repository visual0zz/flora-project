package com.flora.hanako;

import com.flora.hanako.core.HanakoEngine;
import com.flora.hanako.server.HanakoServer;

import java.nio.file.Path;

/**
 * Flora Hanako 启动入口。
 * <p>Java 版 openhanako 个人 AI 助理（方案 B：轻量嵌入式 Web）。
 * 启动 Javalin 服务，浏览器访问即可使用，复刻 openhanako 的记忆 / 人格 / 工具 / 书桌能力。</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path home = Path.of(System.getProperty("user.home"), ".flora-hanako");
        HanakoEngine engine = new HanakoEngine(home);
        engine.applyProviders();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 4567;
        HanakoServer server = new HanakoServer(engine);
        server.start(port);
        System.out.println("[Hanako] 已启动，请浏览器访问 http://localhost:" + port);
    }
}
