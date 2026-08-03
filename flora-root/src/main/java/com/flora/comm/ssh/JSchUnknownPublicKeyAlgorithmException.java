package com.flora.comm.ssh;

public class JSchUnknownPublicKeyAlgorithmException extends JSchHostKeyException {

  private static final long serialVersionUID = -1L;

  JSchUnknownPublicKeyAlgorithmException(String s) {
    super(s);
  }
}
