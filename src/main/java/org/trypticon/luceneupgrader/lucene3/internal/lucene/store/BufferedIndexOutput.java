/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.trypticon.luceneupgrader.lucene3.internal.lucene.store;

import java.io.IOException;

public abstract class BufferedIndexOutput extends IndexOutput {
  static final int BUFFER_SIZE = 16384;

  private final byte[] buffer = new byte[BUFFER_SIZE];
  private long bufferStart = 0;           // position in file of buffer
  private int bufferPosition = 0;         // position in buffer


  @Override
  public void writeByte(byte b) throws IOException {
    if (bufferPosition >= BUFFER_SIZE)
      flush();
    buffer[bufferPosition++] = b;
  }


  @Override
  public void writeBytes(byte[] b, int offset, int length) throws IOException {
    int bytesLeft = BUFFER_SIZE - bufferPosition;
    // is there enough space in the buffer?
    if (bytesLeft >= length) {
      // we add the data to the end of the buffer
      System.arraycopy(b, offset, buffer, bufferPosition, length);
      bufferPosition += length;
      // if the buffer is full, flush it
      if (BUFFER_SIZE - bufferPosition == 0)
        flush();
    } else {
      // is data larger then buffer?
      if (length > BUFFER_SIZE) {
        // we flush the buffer
        if (bufferPosition > 0)
          flush();
        // and write data at once
        flushBuffer(b, offset, length);
        bufferStart += length;
      } else {
        // we fill/flush the buffer (until the input is written)
        int pos = 0; // position in the input data
        int pieceLength;
        while (pos < length) {
          pieceLength = (length - pos < bytesLeft) ? length - pos : bytesLeft;
          System.arraycopy(b, pos + offset, buffer, bufferPosition, pieceLength);
          pos += pieceLength;
          bufferPosition += pieceLength;
          // if the buffer is full, flush it
          bytesLeft = BUFFER_SIZE - bufferPosition;
          if (bytesLeft == 0) {
            flush();
            bytesLeft = BUFFER_SIZE;
          }
        }
      }
    }
  }

  @Override
  public void flush() throws IOException {
    flushBuffer(buffer, bufferPosition);
    bufferStart += bufferPosition;
    bufferPosition = 0;
  }


  private void flushBuffer(byte[] b, int len) throws IOException {
    flushBuffer(b, 0, len);
  }


  protected abstract void flushBuffer(byte[] b, int offset, int len) throws IOException;
  
  @Override
  public void close() throws IOException {
    flush();
  }


  @Override
  public long getFilePointer() {
    return bufferStart + bufferPosition;
  }


  @Override
  public void seek(long pos) throws IOException {
    flush();
    bufferStart = pos;
  }

  @Override
  public abstract long length() throws IOException;


}
