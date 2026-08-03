package com.flora.comm.ssh;

import java.io.IOException;
import java.nio.channels.FileChannel;

public interface FileChannelFactory {
  FileChannel open(String path) throws IOException;
}
