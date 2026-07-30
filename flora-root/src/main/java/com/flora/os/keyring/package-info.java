/**
 * 跨平台操作系统密钥存储（Keyring）。
 * <p>提供统一的 API 在原生密钥链中存储和读取凭据。
 * macOS 使用 {@code security} CLI，Linux 使用 {@code secret-tool} CLI，
 * Windows 使用 DPAPI（通过 PowerShell）。零外部依赖。</p>
 */
package com.flora.os.keyring;
