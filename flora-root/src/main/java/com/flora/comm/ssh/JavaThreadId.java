package com.flora.comm.ssh;

import com.flora.comm.ssh.annotations.SuppressForbiddenApi;

final class JavaThreadId {

  @SuppressForbiddenApi("jdk-deprecated")
  static long get() {
    return Thread.currentThread().getId();
  }
}
