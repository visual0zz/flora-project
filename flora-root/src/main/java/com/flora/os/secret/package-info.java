/**
 * 跨平台操作系统密钥托管。
 * <p>在 OS 最低层级的密钥管理设施中存储/读取/删除密钥。
 * 各平台均通过 FFM API 直接调用原生接口：
 * Linux→内核 keyring（add_key/request_key/keyctl syscall），
 * macOS→Security.framework，
 * Windows→advapi32 CredWriteW/CredReadW（SESSION 级，随会话销毁）。
 * 零外部依赖，非持久化。</p>
 */
package com.flora.os.secret;
