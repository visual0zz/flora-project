package com.flora.runtime.virtual.filesys.bridge;

import com.flora.runtime.virtual.filesys.FSBackend;

/**
 * 挂载解析结果：绑定 {@link FSBackend} 与已裁剪的相对路径。
 * <p>由 {@link MountTable#resolve} 产生，
 * 供 {@link VfsFileSystemProvider} 将 NIO 操作派发到对应后端。</p>
 */
public record FSBackendMatch(String path, FSBackend backend) {
}
