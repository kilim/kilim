/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */
package kilim.nio;

import java.io.ByteArrayInputStream;

/**
 * A hack that exposes the bytearray inside the ByteArrayInputStream. This is to 
 * avoid copying the byte array when toByteArray() is called
 */
public class ExposedBais extends ByteArrayInputStream {

  public ExposedBais(int size) {
      super(new byte[size]);
  }
  
  public ExposedBais(byte[] buf, int offset, int length) {
    super(buf, offset, length);
  }

  public ExposedBais(byte[] buf) {
    super(buf);
  }

  public byte[] toByteArray() {
    return super.buf;
  }

  public void setCount(int n) {
    super.count = n;
  }
}