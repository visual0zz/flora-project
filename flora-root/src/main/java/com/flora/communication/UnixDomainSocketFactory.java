package com.flora.communication;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

/**
 * 基于 JDK {@link UnixDomainSocketAddress}（Java 16+）的 Unix 域套接字工厂，用于连接本地 ssh-agent。
 * <p>替代原 JSch 依赖 junixsocket 的 {@code JUnixSocketFactory}，保持零外部依赖。</p>
 */
public class UnixDomainSocketFactory implements USocketFactory {

  public UnixDomainSocketFactory() throws AgentProxyException {}

  @Override
  public SocketChannel connect(Path path) throws IOException {
    UnixDomainSocketAddress sockAddr = UnixDomainSocketAddress.of(path);
    SocketChannel sock = SocketChannel.open(StandardProtocolFamily.UNIX);
    sock.configureBlocking(true);
    sock.connect(sockAddr);
    return sock;
  }

  @Override
  public ServerSocketChannel bind(Path path) throws IOException {
    UnixDomainSocketAddress sockAddr = UnixDomainSocketAddress.of(path);
    ServerSocketChannel sock = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
    sock.configureBlocking(true);
    sock.bind(sockAddr);
    return sock;
  }
}
