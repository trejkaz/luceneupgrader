package org.apache.lucene.util.packed;

/**
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

import org.apache.lucene.store.DataOutput;
import org.apache.lucene.util.CodecUtil;
import org.apache.lucene.util.Constants;

import java.io.IOException;

/**
 * Simplistic compression for array of unsigned long values.
 * Each value is >= 0 and <= a specified maximum value.  The
 * values are stored as packed ints, with each value
 * consuming a fixed number of bits.
 *
 * @lucene.internal
 */

public class PackedInts {

  private final static String CODEC_NAME = "PackedInts";
  private final static int VERSION_START = 0;
  private final static int VERSION_CURRENT = VERSION_START;

  /**
   * A read-only random access array of positive integers.
   * @lucene.internal
   */
  public interface Reader {
    /**
     * @param index the position of the wanted value.
     * @return the value at the stated index.
     */
    long get(int index);

    /**
     * @return the number of bits used to store any given value.
     *         Note: This does not imply that memory usage is
     *         {@code bitsPerValue * #values} as implementations are free to
     *         use non-space-optimal packing of bits.
     */
    int getBitsPerValue();

    /**
     * @return the number of values.
     */
    int size();

    /**
     * Expert: if the bit-width of this reader matches one of
     * java's native types, returns the underlying array
     * (ie, byte[], short[], int[], long[]); else, returns
     * null.  Note that when accessing the array you must
     * upgrade the type (bitwise AND with all ones), to
     * interpret the full value as unsigned.  Ie,
     * bytes[idx]&0xFF, shorts[idx]&0xFFFF, etc.
     */
    Object getArray();

    /**
     * Returns true if this implementation is backed by a
     * native java array.
     *
     *
     */
    boolean hasArray();
  }
  
  /**
   * A packed integer array that can be modified.
   * @lucene.internal
   */
  public interface Mutable extends Reader {
    /**
     * Set the value at the given index in the array.
     * @param index where the value should be positioned.
     * @param value a value conforming to the constraints set by the array.
     */
    void set(int index, long value);

    /**
     * Sets all values to 0.
     */
    
    void clear();
  }

  /**
   * A simple base for Readers that keeps track of valueCount and bitsPerValue.
   * @lucene.internal
   */
  public static abstract class ReaderImpl implements Reader {
    protected final int bitsPerValue;
    protected final int valueCount;

    protected ReaderImpl(int valueCount, int bitsPerValue) {
      this.bitsPerValue = bitsPerValue;
      assert bitsPerValue > 0 && bitsPerValue <= 64 : "bitsPerValue=" + bitsPerValue;
      this.valueCount = valueCount;
    }

    public int getBitsPerValue() {
      return bitsPerValue;
    }
    
    public int size() {
      return valueCount;
    }

    public Object getArray() {
      return null;
    }

    public boolean hasArray() {
      return false;
    }
  }

  /** A write-once Writer.
   * @lucene.internal
   */
  public static abstract class Writer {
    protected final DataOutput out;
    protected final int bitsPerValue;
    protected final int valueCount;

    protected Writer(DataOutput out, int valueCount, int bitsPerValue)
      throws IOException {
      assert bitsPerValue <= 64;

      this.out = out;
      this.valueCount = valueCount;
      this.bitsPerValue = bitsPerValue;
      CodecUtil.writeHeader(out, CODEC_NAME, VERSION_CURRENT);
      out.writeVInt(bitsPerValue);
      out.writeVInt(valueCount);
    }

    public abstract void add(long v) throws IOException;
    public abstract void finish() throws IOException;
  }

  /**
   * Create a packed integer array with the given amount of values initialized
   * to 0. the valueCount and the bitsPerValue cannot be changed after creation.
   * All Mutables known by this factory are kept fully in RAM.
   * @param valueCount   the number of elements.
   * @param bitsPerValue the number of bits available for any given value.
   * @return a mutable packed integer array.
   * @lucene.internal
   */
  public static Mutable getMutable(
         int valueCount, int bitsPerValue) {
    switch (bitsPerValue) {
    case 8:
      return new Direct8(valueCount);
    case 16:
      return new Direct16(valueCount);
    case 32:
      return new Direct32(valueCount);
    case 64:
      return new Direct64(valueCount);
    default:
      if (Constants.JRE_IS_64BIT || bitsPerValue >= 32) {
        return new Packed64(valueCount, bitsPerValue);
      } else {
        return new Packed32(valueCount, bitsPerValue);
      }
    }
  }

  /**
   * Calculates the maximum unsigned long that can be expressed with the given
   * number of bits.
   * @param bitsPerValue the number of bits available for any given value.
   * @return the maximum value for the given bits.
   * @lucene.internal
   */
  public static long maxValue(int bitsPerValue) {
    return bitsPerValue == 64 ? Long.MAX_VALUE : ~(~0L << bitsPerValue);
  }

  /** Rounds bitsPerValue up to 8, 16, 32 or 64. */
  public static int getNextFixedSize(int bitsPerValue) {
    if (bitsPerValue <= 8) {
      return 8;
    } else if (bitsPerValue <= 16) {
      return 16;
    } else if (bitsPerValue <= 32) {
      return 32;
    } else {
      return 64;
    }
  }
}