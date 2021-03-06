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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene40;

import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.MultiLevelSkipListReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.store.IndexInput;

@Deprecated
final class Lucene40SkipListReader extends MultiLevelSkipListReader {
  private boolean currentFieldStoresPayloads;
  private boolean currentFieldStoresOffsets;
  private long freqPointer[];
  private long proxPointer[];
  private int payloadLength[];
  private int offsetLength[];
  
  private long lastFreqPointer;
  private long lastProxPointer;
  private int lastPayloadLength;
  private int lastOffsetLength;
                           
  public Lucene40SkipListReader(IndexInput skipStream, int maxSkipLevels, int skipInterval) {
    super(skipStream, maxSkipLevels, skipInterval);
    freqPointer = new long[maxSkipLevels];
    proxPointer = new long[maxSkipLevels];
    payloadLength = new int[maxSkipLevels];
    offsetLength = new int[maxSkipLevels];
  }

  public void init(long skipPointer, long freqBasePointer, long proxBasePointer, int df, boolean storesPayloads, boolean storesOffsets) throws IOException {
    super.init(skipPointer, df);
    this.currentFieldStoresPayloads = storesPayloads;
    this.currentFieldStoresOffsets = storesOffsets;
    lastFreqPointer = freqBasePointer;
    lastProxPointer = proxBasePointer;

    Arrays.fill(freqPointer, freqBasePointer);
    Arrays.fill(proxPointer, proxBasePointer);
    Arrays.fill(payloadLength, 0);
    Arrays.fill(offsetLength, 0);
  }

  public long getFreqPointer() {
    return lastFreqPointer;
  }

  public long getProxPointer() {
    return lastProxPointer;
  }
  

  public int getPayloadLength() {
    return lastPayloadLength;
  }
  

  public int getOffsetLength() {
    return lastOffsetLength;
  }
  
  @Override
  protected void seekChild(int level) throws IOException {
    super.seekChild(level);
    freqPointer[level] = lastFreqPointer;
    proxPointer[level] = lastProxPointer;
    payloadLength[level] = lastPayloadLength;
    offsetLength[level] = lastOffsetLength;
  }
  
  @Override
  protected void setLastSkipData(int level) {
    super.setLastSkipData(level);
    lastFreqPointer = freqPointer[level];
    lastProxPointer = proxPointer[level];
    lastPayloadLength = payloadLength[level];
    lastOffsetLength = offsetLength[level];
  }


  @Override
  protected int readSkipData(int level, IndexInput skipStream) throws IOException {
    int delta;
    if (currentFieldStoresPayloads || currentFieldStoresOffsets) {
      // the current field stores payloads and/or offsets.
      // if the doc delta is odd then we have
      // to read the current payload/offset lengths
      // because it differs from the lengths of the
      // previous payload/offset
      delta = skipStream.readVInt();
      if ((delta & 1) != 0) {
        if (currentFieldStoresPayloads) {
          payloadLength[level] = skipStream.readVInt();
        }
        if (currentFieldStoresOffsets) {
          offsetLength[level] = skipStream.readVInt();
        }
      }
      delta >>>= 1;
    } else {
      delta = skipStream.readVInt();
    }

    freqPointer[level] += skipStream.readVLong();
    proxPointer[level] += skipStream.readVLong();
    
    return delta;
  }
}
