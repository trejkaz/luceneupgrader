package org.trypticon.luceneupgrader.lucene3.internal.lucene.util.packed;

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

/**
 * Direct wrapping of 16 bit values to a backing array of shorts.
 * @lucene.internal
 */

class Direct16 extends PackedInts.ReaderImpl
        implements PackedInts.Mutable {
  private short[] values;
  private static final int BITS_PER_VALUE = 16;

  public Direct16(int valueCount) {
    super(valueCount, BITS_PER_VALUE);
    values = new short[valueCount];
  }

  public long get(final int index) {
    assert index >= 0 && index < size();
    return 0xFFFFL & values[index];
  }

  public void set(final int index, final long value) {
    values[index] = (short) (value & 0xFFFF);
  }

}