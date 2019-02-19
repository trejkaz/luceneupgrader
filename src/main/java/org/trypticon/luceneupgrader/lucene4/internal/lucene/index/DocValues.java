package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

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

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

public final class DocValues {

  /* no instantiation */
  private DocValues() {}


  public static final BinaryDocValues emptyBinary() {
    final BytesRef empty = new BytesRef();
    return new BinaryDocValues() {
      @Override
      public BytesRef get(int docID) {
        return empty;
      }
    };
  }


  public static final NumericDocValues emptyNumeric() {
    return new NumericDocValues() {
      @Override
      public long get(int docID) {
        return 0;
      }
    };
  }


  public static final SortedDocValues emptySorted() {
    final BytesRef empty = new BytesRef();
    return new SortedDocValues() {
      @Override
      public int getOrd(int docID) {
        return -1;
      }

      @Override
      public BytesRef lookupOrd(int ord) {
        return empty;
      }

      @Override
      public int getValueCount() {
        return 0;
      }
    };
  }

  public static final SortedNumericDocValues emptySortedNumeric(int maxDoc) {
    return singleton(emptyNumeric(), new Bits.MatchNoBits(maxDoc));
  }


  public static final RandomAccessOrds emptySortedSet() {
    return singleton(emptySorted());
  }
  

  public static RandomAccessOrds singleton(SortedDocValues dv) {
    return new SingletonSortedSetDocValues(dv);
  }
  

  public static SortedDocValues unwrapSingleton(SortedSetDocValues dv) {
    if (dv instanceof SingletonSortedSetDocValues) {
      return ((SingletonSortedSetDocValues)dv).getSortedDocValues();
    } else {
      return null;
    }
  }
  

  public static NumericDocValues unwrapSingleton(SortedNumericDocValues dv) {
    if (dv instanceof SingletonSortedNumericDocValues) {
      return ((SingletonSortedNumericDocValues)dv).getNumericDocValues();
    } else {
      return null;
    }
  }
  

  public static Bits unwrapSingletonBits(SortedNumericDocValues dv) {
    if (dv instanceof SingletonSortedNumericDocValues) {
      return ((SingletonSortedNumericDocValues)dv).getDocsWithField();
    } else {
      return null;
    }
  }
  
  public static SortedNumericDocValues singleton(NumericDocValues dv, Bits docsWithField) {
    return new SingletonSortedNumericDocValues(dv, docsWithField);
  }
  
  public static Bits docsWithValue(final SortedDocValues dv, final int maxDoc) {
    return new Bits() {
      @Override
      public boolean get(int index) {
        return dv.getOrd(index) >= 0;
      }

      @Override
      public int length() {
        return maxDoc;
      }
    };
  }
  
  public static Bits docsWithValue(final SortedSetDocValues dv, final int maxDoc) {
    return new Bits() {
      @Override
      public boolean get(int index) {
        dv.setDocument(index);
        return dv.nextOrd() != SortedSetDocValues.NO_MORE_ORDS;
      }

      @Override
      public int length() {
        return maxDoc;
      }
    };
  }
  
  public static Bits docsWithValue(final SortedNumericDocValues dv, final int maxDoc) {
    return new Bits() {
      @Override
      public boolean get(int index) {
        dv.setDocument(index);
        return dv.count() != 0;
      }

      @Override
      public int length() {
        return maxDoc;
      }
    };
  }
  
  // some helpers, for transition from fieldcache apis.
  // as opposed to the AtomicReader apis (which must be strict for consistency), these are lenient
  
  public static NumericDocValues getNumeric(AtomicReader in, String field) throws IOException {
    NumericDocValues dv = in.getNumericDocValues(field);
    if (dv == null) {
      return emptyNumeric();
    } else {
      return dv;
    }
  }
  
  public static BinaryDocValues getBinary(AtomicReader in, String field) throws IOException {
    BinaryDocValues dv = in.getBinaryDocValues(field);
    if (dv == null) {
      dv = in.getSortedDocValues(field);
      if (dv == null) {
        return emptyBinary();
      }
    }
    return dv;
  }
  
  public static SortedDocValues getSorted(AtomicReader in, String field) throws IOException {
    SortedDocValues dv = in.getSortedDocValues(field);
    if (dv == null) {
      return emptySorted();
    } else {
      return dv;
    }
  }
  
  public static SortedNumericDocValues getSortedNumeric(AtomicReader in, String field) throws IOException {
    SortedNumericDocValues dv = in.getSortedNumericDocValues(field);
    if (dv == null) {
      NumericDocValues single = in.getNumericDocValues(field);
      if (single == null) {
        return emptySortedNumeric(in.maxDoc());
      }
      Bits bits = in.getDocsWithField(field);
      return singleton(single, bits);
    }
    return dv;
  }
  
  public static SortedSetDocValues getSortedSet(AtomicReader in, String field) throws IOException {
    SortedSetDocValues dv = in.getSortedSetDocValues(field);
    if (dv == null) {
      SortedDocValues sorted = in.getSortedDocValues(field);
      if (sorted == null) {
        return emptySortedSet();
      }
      return singleton(sorted);
    }
    return dv;
  }
  
  public static Bits getDocsWithField(AtomicReader in, String field) throws IOException {
    Bits dv = in.getDocsWithField(field);
    if (dv == null) {
      return new Bits.MatchNoBits(in.maxDoc());
    } else {
      return dv;
    }
  }
}
