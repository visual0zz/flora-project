/**
 * 虚拟文件系统框架包。
 * <p>定义文件系统抽象（{@code com.flora.runtime.virtual.file}）
 * 与 NIO.2 集成（{@code com.flora.runtime.virtual.file.nio}），
 * 支持内存文件系统和真实文件系统两种后端。</p>
 * <p>使用示例：</p>
 * <pre>{@code
 * VfsFileSystem fs = new VfsFileSystem();
 * fs.mount("/data", new MemoryFileSystem());
 * Path p = fs.getPath("/data/hello.txt");
 * Files.writeString(p, "Hello!");
 * String text = Files.readString(p);
 * }</pre>
 */
package com.flora.runtime.virtual;
