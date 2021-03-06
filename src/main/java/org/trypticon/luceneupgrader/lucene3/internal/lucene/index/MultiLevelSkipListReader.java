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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.io.IOException;
import java.util.Arrays;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.BufferedIndexInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.IndexInput;

abstract class MultiLevelSkipListReader {
  // the maximum number of skip levels possible for this index
  private int maxNumberOfSkipLevels; 
  
  // number of levels in this skip list
  private int numberOfSkipLevels;
  
  // Expert: defines the number of top skip levels to buffer in memory.
  // Reducing this number results in less memory usage, but possibly
  // slower performance due to more random I/Os.
  // Please notice that the space each level occupies is limited by
  // the skipInterval. The top level can not contain more than
  // skipLevel entries, the second top level can not contain more
  // than skipLevel^2 entries and so forth.
  private int numberOfLevelsToBuffer = 1;
  
  private int docCount;
  private boolean haveSkipped;
  
  private IndexInput[] skipStream;    // skipStream for each level
  private long skipPointer[];         // the start pointer of each skip level
  private int skipInterval[];         // skipInterval of each level
  private int[] numSkipped;           // number of docs skipped per level
    
  private int[] skipDoc;              // doc id of current skip entry per level 
  private int lastDoc;                // doc id of last read skip entry with docId <= target
  private long[] childPointer;        // child pointer of current skip entry per level
  private long lastChildPointer;      // childPointer of last read skip entry with docId <= target
  
  private boolean inputIsBuffered;
  
  public MultiLevelSkipListReader(IndexInput skipStream, int maxSkipLevels, int skipInterval) {
    this.skipStream = new IndexInput[maxSkipLevels];
    this.skipPointer = new long[maxSkipLevels];
    this.childPointer = new long[maxSkipLevels];
    this.numSkipped = new int[maxSkipLevels];
    this.maxNumberOfSkipLevels = maxSkipLevels;
    this.skipInterval = new int[maxSkipLevels];
    this.skipStream [0]= skipStream;
    this.inputIsBuffered = (skipStream instanceof BufferedIndexInput);
    this.skipInterval[0] = skipInterval;
    for (int i = 1; i < maxSkipLevels; i++) {
      // cache skip intervals
      this.skipInterval[i] = this.skipInterval[i - 1] * skipInterval;
    }
    skipDoc = new int[maxSkipLevels];
  }

  
  int getDoc() {
    return lastDoc;
  }
  
  

  int skipTo(int target) throws IOException {
    if (!haveSkipped) {
      // first time, load skip levels
      loadSkipLevels();
      haveSkipped = true;
    }
  
    // walk up the levels until highest level is found that has a skip
    // for this target
    int level = 0;
    while (level < numberOfSkipLevels - 1 && target > skipDoc[level + 1]) {
      level++;
    }    

    while (level >= 0) {
      if (target > skipDoc[level]) {
        if (!loadNextSkip(level)) {
          continue;
        }
      } else {
        // no more skips on this level, go down one level
        if (level > 0 && lastChildPointer > skipStream[level - 1].getFilePointer()) {
          seekChild(level - 1);
        } 
        level--;
      }
    }
    
    return numSkipped[0] - skipInterval[0] - 1;
  }
  
  private boolean loadNextSkip(int level) throws IOException {
    // we have to skip, the target document is greater than the current
    // skip list entry        
    setLastSkipData(level);
      
    numSkipped[level] += skipInterval[level];
      
    if (numSkipped[level] > docCount) {
      // this skip list is exhausted
      skipDoc[level] = Integer.MAX_VALUE;
      if (numberOfSkipLevels > level) numberOfSkipLevels = level; 
      return false;
    }

    // read next skip entry
    skipDoc[level] += readSkipData(level, skipStream[level]);
    
    if (level != 0) {
      // read the child pointer if we are not on the leaf level
      childPointer[level] = skipStream[level].readVLong() + skipPointer[level - 1];
    }
    
    return true;

  }
  
  protected void seekChild(int level) throws IOException {
    skipStream[level].seek(lastChildPointer);
    numSkipped[level] = numSkipped[level + 1] - skipInterval[level + 1];
    skipDoc[level] = lastDoc;
    if (level > 0) {
        childPointer[level] = skipStream[level].readVLong() + skipPointer[level - 1];
    }
  }
  
  void close() throws IOException {
    for (int i = 1; i < skipStream.length; i++) {
      if (skipStream[i] != null) {
        skipStream[i].close();
      }
    }
  }

  void init(long skipPointer, int df) {
    this.skipPointer[0] = skipPointer;
    this.docCount = df;
    Arrays.fill(skipDoc, 0);
    Arrays.fill(numSkipped, 0);
    Arrays.fill(childPointer, 0);
    
    haveSkipped = false;
    for (int i = 1; i < numberOfSkipLevels; i++) {
      skipStream[i] = null;
    }
  }
  
  private void loadSkipLevels() throws IOException {
    numberOfSkipLevels = docCount == 0 ? 0 : (int) Math.floor(Math.log(docCount) / Math.log(skipInterval[0]));
    if (numberOfSkipLevels > maxNumberOfSkipLevels) {
      numberOfSkipLevels = maxNumberOfSkipLevels;
    }

    skipStream[0].seek(skipPointer[0]);
    
    int toBuffer = numberOfLevelsToBuffer;
    
    for (int i = numberOfSkipLevels - 1; i > 0; i--) {
      // the length of the current level
      long length = skipStream[0].readVLong();
      
      // the start pointer of the current level
      skipPointer[i] = skipStream[0].getFilePointer();
      if (toBuffer > 0) {
        // buffer this level
        skipStream[i] = new SkipBuffer(skipStream[0], (int) length);
        toBuffer--;
      } else {
        // clone this stream, it is already at the start of the current level
        skipStream[i] = (IndexInput) skipStream[0].clone();
        if (inputIsBuffered && length < BufferedIndexInput.BUFFER_SIZE) {
          ((BufferedIndexInput) skipStream[i]).setBufferSize((int) length);
        }
        
        // move base stream beyond the current level
        skipStream[0].seek(skipStream[0].getFilePointer() + length);
      }
    }
   
    // use base stream for the lowest level
    skipPointer[0] = skipStream[0].getFilePointer();
  }
  
  protected abstract int readSkipData(int level, IndexInput skipStream) throws IOException;
  
  protected void setLastSkipData(int level) {
    lastDoc = skipDoc[level];
    lastChildPointer = childPointer[level];
  }

  
  private final static class SkipBuffer extends IndexInput {
    private byte[] data;
    private long pointer;
    private int pos;
    
    SkipBuffer(IndexInput input, int length) throws IOException {
      super("SkipBuffer on " + input);
      data = new byte[length];
      pointer = input.getFilePointer();
      input.readBytes(data, 0, length);
    }
    
    @Override
    public void close() throws IOException {
      data = null;
    }

    @Override
    public long getFilePointer() {
      return pointer + pos;
    }

    @Override
    public long length() {
      return data.length;
    }

    @Override
    public byte readByte() throws IOException {
      return data[pos++];
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
      System.arraycopy(data, pos, b, offset, len);
      pos += len;
    }

    @Override
    public void seek(long pos) throws IOException {
      this.pos =  (int) (pos - pointer);
    }
    
  }
}
