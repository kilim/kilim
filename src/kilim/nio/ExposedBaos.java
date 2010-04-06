/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.nio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * A hack that exposes the bytearray inside the ByteArrayOutputStream. This is to 
 * avoid copying the byte array when toByteArray() is called.
 */

public class ExposedBaos extends ByteArrayOutputStream {
  public ExposedBaos() {
    super();
  }

  public ExposedBaos(int size) {
    super(size);
  }

  @Override
  public byte[] toByteArray() {
    return buf;
  }

  public ByteBuffer toByteBuffer() {
    return ByteBuffer.wrap(buf, 0, count);
  }

  public void setCount(int n) {
    super.count = n;
  }
}
