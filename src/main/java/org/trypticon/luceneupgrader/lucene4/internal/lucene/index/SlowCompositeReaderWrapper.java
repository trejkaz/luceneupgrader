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
import java.util.HashMap;
import java.util.Map;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.FieldInfo.DocValuesType;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MultiDocValues.MultiSortedDocValues;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MultiDocValues.MultiSortedSetDocValues;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MultiDocValues.OrdinalMap;

public final class SlowCompositeReaderWrapper extends AtomicReader {

  private final CompositeReader in;
  private final Fields fields;
  private final Bits liveDocs;
  

  public static AtomicReader wrap(IndexReader reader) throws IOException {
    if (reader instanceof CompositeReader) {
      return new SlowCompositeReaderWrapper((CompositeReader) reader);
    } else {
      assert reader instanceof AtomicReader;
      return (AtomicReader) reader;
    }
  }

  private SlowCompositeReaderWrapper(CompositeReader reader) throws IOException {
    super();
    in = reader;
    fields = MultiFields.getFields(in);
    liveDocs = MultiFields.getLiveDocs(in);
    in.registerParentReader(this);
  }

  @Override
  public String toString() {
    return "SlowCompositeReaderWrapper(" + in + ")";
  }

  @Override
  public void addCoreClosedListener(CoreClosedListener listener) {
    addCoreClosedListenerAsReaderClosedListener(in, listener);
  }

  @Override
  public void removeCoreClosedListener(CoreClosedListener listener) {
    removeCoreClosedListenerAsReaderClosedListener(in, listener);
  }

  @Override
  public Fields fields() {
    ensureOpen();
    return fields;
  }

  @Override
  public NumericDocValues getNumericDocValues(String field) throws IOException {
    ensureOpen();
    return MultiDocValues.getNumericValues(in, field);
  }

  @Override
  public Bits getDocsWithField(String field) throws IOException {
    ensureOpen();
    return MultiDocValues.getDocsWithField(in, field);
  }

  @Override
  public BinaryDocValues getBinaryDocValues(String field) throws IOException {
    ensureOpen();
    return MultiDocValues.getBinaryValues(in, field);
  }
  
  @Override
  public SortedNumericDocValues getSortedNumericDocValues(String field) throws IOException {
    ensureOpen();
    return MultiDocValues.getSortedNumericValues(in, field);
  }

  @Override
  public SortedDocValues getSortedDocValues(String field) throws IOException {
    ensureOpen();
    OrdinalMap map = null;
    synchronized (cachedOrdMaps) {
      map = cachedOrdMaps.get(field);
      if (map == null) {
        // uncached, or not a multi dv
        SortedDocValues dv = MultiDocValues.getSortedValues(in, field);
        if (dv instanceof MultiSortedDocValues) {
          map = ((MultiSortedDocValues)dv).mapping;
          if (map.owner == getCoreCacheKey()) {
            cachedOrdMaps.put(field, map);
          }
        }
        return dv;
      }
    }
    // cached ordinal map
    if (getFieldInfos().fieldInfo(field).getDocValuesType() != DocValuesType.SORTED) {
      return null;
    }
    int size = in.leaves().size();
    final SortedDocValues[] values = new SortedDocValues[size];
    final int[] starts = new int[size+1];
    for (int i = 0; i < size; i++) {
      AtomicReaderContext context = in.leaves().get(i);
      SortedDocValues v = context.reader().getSortedDocValues(field);
      if (v == null) {
        v = DocValues.emptySorted();
      }
      values[i] = v;
      starts[i] = context.docBase;
    }
    starts[size] = maxDoc();
    return new MultiSortedDocValues(values, starts, map);
  }
  
  @Override
  public SortedSetDocValues getSortedSetDocValues(String field) throws IOException {
    ensureOpen();
    OrdinalMap map = null;
    synchronized (cachedOrdMaps) {
      map = cachedOrdMaps.get(field);
      if (map == null) {
        // uncached, or not a multi dv
        SortedSetDocValues dv = MultiDocValues.getSortedSetValues(in, field);
        if (dv instanceof MultiSortedSetDocValues) {
          map = ((MultiSortedSetDocValues)dv).mapping;
          if (map.owner == getCoreCacheKey()) {
            cachedOrdMaps.put(field, map);
          }
        }
        return dv;
      }
    }
    // cached ordinal map
    if (getFieldInfos().fieldInfo(field).getDocValuesType() != DocValuesType.SORTED_SET) {
      return null;
    }
    assert map != null;
    int size = in.leaves().size();
    final SortedSetDocValues[] values = new SortedSetDocValues[size];
    final int[] starts = new int[size+1];
    for (int i = 0; i < size; i++) {
      AtomicReaderContext context = in.leaves().get(i);
      SortedSetDocValues v = context.reader().getSortedSetDocValues(field);
      if (v == null) {
        v = DocValues.emptySortedSet();
      }
      values[i] = v;
      starts[i] = context.docBase;
    }
    starts[size] = maxDoc();
    return new MultiSortedSetDocValues(values, starts, map);
  }
  
  // TODO: this could really be a weak map somewhere else on the coreCacheKey,
  // but do we really need to optimize slow-wrapper any more?
  private final Map<String,OrdinalMap> cachedOrdMaps = new HashMap<>();

  @Override
  public NumericDocValues getNormValues(String field) throws IOException {
    ensureOpen();
    return MultiDocValues.getNormValues(in, field);
  }
  
  @Override
  public Fields getTermVectors(int docID) throws IOException {
    ensureOpen();
    return in.getTermVectors(docID);
  }

  @Override
  public int numDocs() {
    // Don't call ensureOpen() here (it could affect performance)
    return in.numDocs();
  }

  @Override
  public int maxDoc() {
    // Don't call ensureOpen() here (it could affect performance)
    return in.maxDoc();
  }

  @Override
  public void document(int docID, StoredFieldVisitor visitor) throws IOException {
    ensureOpen();
    in.document(docID, visitor);
  }

  @Override
  public Bits getLiveDocs() {
    ensureOpen();
    return liveDocs;
  }

  @Override
  public FieldInfos getFieldInfos() {
    ensureOpen();
    return MultiFields.getMergedFieldInfos(in);
  }

  @Override
  public Object getCoreCacheKey() {
    return in.getCoreCacheKey();
  }

  @Override
  public Object getCombinedCoreAndDeletesKey() {
    return in.getCombinedCoreAndDeletesKey();
  }

  @Override
  protected void doClose() throws IOException {
    // TODO: as this is a wrapper, should we really close the delegate?
    in.close();
  }

  @Override
  public void checkIntegrity() throws IOException {
    ensureOpen();
    for (AtomicReaderContext ctx : in.leaves()) {
      ctx.reader().checkIntegrity();
    }
  }
}
