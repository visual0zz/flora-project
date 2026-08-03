package com.flora.comm.ssh;

public class JSchUnknownCAKeyException extends JSchHostKeyException {

  private static final long serialVersionUID = -1L;

  JSchUnknownCAKeyException(String s) {
    super(s);
  }
}
