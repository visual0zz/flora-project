package com.flora.comm.ssh.crypto;

/** SHA-384 摘要适配 */
public class Sha384 extends FloraDigest {
  public Sha384() {
    super("SHA-384", 48, "SHA384");
  }
}
