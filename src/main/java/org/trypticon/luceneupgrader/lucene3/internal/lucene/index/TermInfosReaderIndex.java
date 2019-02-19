package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.BitUtil;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.BytesRef;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.PagedBytes.PagedBytesDataInput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.PagedBytes.PagedBytesDataOutput;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.PagedBytes;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.packed.GrowableWriter;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.packed.PackedInts;

class TermInfosReaderIndex {

  private static final int MAX_PAGE_BITS = 18; // 256 KB block
  private Term[] fields;
  private int totalIndexInterval;
  private Comparator<BytesRef> comparator = BytesRef.getUTF8SortedAsUTF16Comparator();
  private final PagedBytesDataInput dataInput;
  private final PackedInts.Reader indexToDataOffset;
  private final int indexSize;
  private final int skipInterval;

  TermInfosReaderIndex(SegmentTermEnum indexEnum, int indexDivisor, long tiiFileLength, int totalIndexInterval) throws IOException {
    this.totalIndexInterval = totalIndexInterval;
    indexSize = 1 + ((int) indexEnum.size - 1) / indexDivisor;
    skipInterval = indexEnum.skipInterval;
    // this is only an inital size, it will be GCed once the build is complete
    long initialSize = (long) (tiiFileLength * 1.5) / indexDivisor;
    PagedBytes dataPagedBytes = new PagedBytes(estimatePageBits(initialSize));
    PagedBytesDataOutput dataOutput = dataPagedBytes.getDataOutput();

    GrowableWriter indexToTerms = new GrowableWriter(4, indexSize, false);
    String currentField = null;
    List<String> fieldStrs = new ArrayList<String>();
    int fieldCounter = -1;
    for (int i = 0; indexEnum.next(); i++) {
      Term term = indexEnum.term();
      if (currentField != term.field) {
        currentField = term.field;
        fieldStrs.add(currentField);
        fieldCounter++;
      }
      TermInfo termInfo = indexEnum.termInfo();
      indexToTerms.set(i, dataOutput.getPosition());
      dataOutput.writeVInt(fieldCounter);
      dataOutput.writeString(term.text());
      dataOutput.writeVInt(termInfo.docFreq);
      if (termInfo.docFreq >= skipInterval) {
        dataOutput.writeVInt(termInfo.skipOffset);
      }
      dataOutput.writeVLong(termInfo.freqPointer);
      dataOutput.writeVLong(termInfo.proxPointer);
      dataOutput.writeVLong(indexEnum.indexPointer);
      for (int j = 1; j < indexDivisor; j++) {
        if (!indexEnum.next()) {
          break;
        }
      }
    }

    fields = new Term[fieldStrs.size()];
    for (int i = 0; i < fields.length; i++) {
      fields[i] = new Term(fieldStrs.get(i));
    }
    
    dataPagedBytes.freeze(true);
    dataInput = dataPagedBytes.getDataInput();
    indexToDataOffset = indexToTerms.getMutable();
  }

  private static int estimatePageBits(long estSize) {
    return Math.max(Math.min(64 - BitUtil.nlz(estSize), MAX_PAGE_BITS), 4);
  }

  void seekEnum(SegmentTermEnum enumerator, int indexOffset) throws IOException {
    PagedBytesDataInput input = (PagedBytesDataInput) dataInput.clone();
    
    input.setPosition(indexToDataOffset.get(indexOffset));

    // read the term
    int fieldId = input.readVInt();
    Term field = fields[fieldId];
    Term term = field.createTerm(input.readString());

    // read the terminfo
    TermInfo termInfo = new TermInfo();
    termInfo.docFreq = input.readVInt();
    if (termInfo.docFreq >= skipInterval) {
      termInfo.skipOffset = input.readVInt();
    } else {
      termInfo.skipOffset = 0;
    }
    termInfo.freqPointer = input.readVLong();
    termInfo.proxPointer = input.readVLong();

    long pointer = input.readVLong();

    // perform the seek
    enumerator.seek(pointer, ((long) indexOffset * totalIndexInterval) - 1, term, termInfo);
  }

  int getIndexOffset(Term term, BytesRef termBytesRef) throws IOException {
    int lo = 0;
    int hi = indexSize - 1;
    PagedBytesDataInput input = (PagedBytesDataInput) dataInput.clone();
    BytesRef scratch = new BytesRef();
    while (hi >= lo) {
      int mid = (lo + hi) >>> 1;
      int delta = compareTo(term, termBytesRef, mid, input, scratch);
      if (delta < 0)
        hi = mid - 1;
      else if (delta > 0)
        lo = mid + 1;
      else
        return mid;
    }
    return hi;
  }

  Term getTerm(int termIndex) throws IOException {
    PagedBytesDataInput input = (PagedBytesDataInput) dataInput.clone();
    input.setPosition(indexToDataOffset.get(termIndex));

    // read the term
    int fieldId = input.readVInt();
    Term field = fields[fieldId];
    return field.createTerm(input.readString());
  }

  int length() {
    return indexSize;
  }

  int compareTo(Term term, BytesRef termBytesRef, int termIndex) throws IOException {
    return compareTo(term, termBytesRef, termIndex, (PagedBytesDataInput) dataInput.clone(), new BytesRef());
  }

  private int compareTo(Term term, BytesRef termBytesRef, int termIndex, PagedBytesDataInput input, BytesRef reuse) throws IOException {
    // if term field does not equal mid's field index, then compare fields
    // else if they are equal, compare term's string values...
    int c = compareField(term, termIndex, input);
    if (c == 0) {
      reuse.length = input.readVInt();
      reuse.grow(reuse.length);
      input.readBytes(reuse.bytes, 0, reuse.length);
      return comparator.compare(termBytesRef, reuse);
    }
    return c;
  }

  private int compareField(Term term, int termIndex, PagedBytesDataInput input) throws IOException {
    input.setPosition(indexToDataOffset.get(termIndex));
    return term.field.compareTo(fields[input.readVInt()].field);
  }
}
