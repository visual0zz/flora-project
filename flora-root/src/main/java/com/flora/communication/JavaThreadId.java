package com.flora.communication;

import com.flora.communication.annotations.SuppressForbiddenApi;

final class JavaThreadId {

  @SuppressForbiddenApi("jdk-deprecated")
  static long get() {
    return Thread.currentThread().getId();
  }
}
