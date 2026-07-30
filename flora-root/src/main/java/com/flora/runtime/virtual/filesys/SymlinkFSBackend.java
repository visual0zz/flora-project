package com.flora.runtime.virtual.filesys;

import java.io.IOException;

/**
 * 支持符号链接的虚拟文件系统后端接口。
 * <p>在 {@link FSBackend} 基础上增加符号链接的创建与读取能力。</p>
 */
public interface SymlinkFSBackend extends FSBackend {

    /** 创建符号链接。返回 false 若 path 已存在。 */
    boolean createSymbolicLink(String path, String target) throws IOException;

    /** 读取符号链接的目标路径。路径非符号链接时抛出 IOException。 */
    String readSymbolicLink(String path) throws IOException;
}
