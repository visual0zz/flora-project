package com.flora.comm.ssh;

/**
 * 通信层版本号。
 * <p>原 JSch 通过 Maven 资源模板注入版本，吸收进 flora-root 后改为常量。</p>
 */
final class Version {

    private static final String VERSION = "2.28.7";

    static String getVersion() {
        return VERSION;
    }
}
