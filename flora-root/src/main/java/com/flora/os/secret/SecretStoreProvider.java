package com.flora.os.secret;

/**
 * SecretStore 实例工厂。自动检测操作系统并返回对应的实现。
 */
public class SecretStoreProvider {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    private SecretStoreProvider() {}

    /** 创建当前平台对应的 SecretStore 实例。 */
    public static SecretStore create() throws SecretStoreException {
        if (OS_NAME.contains("mac")) {
            return new MacSecretStore();
        }
        if (OS_NAME.contains("linux")) {
            return new LinuxSecretStore();
        }
        if (OS_NAME.contains("win")) {
            return new WinSecretStore();
        }
        throw new SecretStoreException("不支持的操作系统: " + OS_NAME);
    }

    /** 检查当前平台秘密存储是否可用。 */
    public static boolean isAvailable() {
        if (!isSupported()) return false;
        return switch (getProviderName()) {
            case "macOS Keychain (FFM)" -> MacSecretStore.isAvailable();
            case "Linux Kernel Keyring" -> LinuxSecretStore.isAvailable();
            case "Windows Credential Manager (SESSION)" -> WinSecretStore.isAvailable();
            default -> false;
        };
    }

    /** 返回当前平台支持的 SecretStore 类型。 */
    public static String getProviderName() {
        if (OS_NAME.contains("mac")) return "macOS Keychain (FFM)";
        if (OS_NAME.contains("linux")) return "Linux Kernel Keyring";
        if (OS_NAME.contains("win")) return "Windows Credential Manager (SESSION)";
        return "unknown";
    }

    /** 检查当前平台是否支持秘密存储。 */
    public static boolean isSupported() {
        return OS_NAME.contains("mac") || OS_NAME.contains("linux") || OS_NAME.contains("win");
    }
}
