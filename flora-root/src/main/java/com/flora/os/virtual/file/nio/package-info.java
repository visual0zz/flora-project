/**
 * NIO.2 文件系统 SPI 集成包。
 * <p>实现 {@code java.nio.file.spi.FileSystemProvider} SPI，
 * 将虚拟文件系统注册为 {@code vfs://} URI 可访问的文件系统，
 * 提供 {@link java.nio.file.Path}、{@link java.nio.channels.Channel} 等标准 NIO.2 接口。</p>
 */
package com.flora.os.virtual.file.nio;
