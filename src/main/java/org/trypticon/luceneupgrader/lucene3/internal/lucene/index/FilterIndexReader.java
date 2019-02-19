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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.Document;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.document.FieldSelector;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;

import java.io.IOException;
import java.util.Map;

public class FilterIndexReader extends IndexReader {

  public static class FilterTermDocs implements TermDocs {
    protected TermDocs in;

    public FilterTermDocs(TermDocs in) { this.in = in; }

    public void seek(Term term) throws IOException { in.seek(term); }
    public void seek(TermEnum termEnum) throws IOException { in.seek(termEnum); }
    public int doc() { return in.doc(); }
    public int freq() { return in.freq(); }
    public boolean next() throws IOException { return in.next(); }
    public int read(int[] docs, int[] freqs) throws IOException {
      return in.read(docs, freqs);
    }
    public boolean skipTo(int i) throws IOException { return in.skipTo(i); }
    public void close() throws IOException { in.close(); }
  }

  public static class FilterTermPositions
          extends FilterTermDocs implements TermPositions {

    public FilterTermPositions(TermPositions in) { super(in); }

    public int nextPosition() throws IOException {
      return ((TermPositions) this.in).nextPosition();
    }
    
    public int getPayloadLength() {
      return ((TermPositions) this.in).getPayloadLength();
    }

    public byte[] getPayload(byte[] data, int offset) throws IOException {
      return ((TermPositions) this.in).getPayload(data, offset);
    }


    // TODO: Remove warning after API has been finalized
    public boolean isPayloadAvailable() {
      return ((TermPositions)this.in).isPayloadAvailable();
    }
  }

  public static class FilterTermEnum extends TermEnum {
    protected TermEnum in;

    public FilterTermEnum(TermEnum in) { this.in = in; }

    @Override
    public boolean next() throws IOException { return in.next(); }
    @Override
    public Term term() { return in.term(); }
    @Override
    public int docFreq() { return in.docFreq(); }
    @Override
    public void close() throws IOException { in.close(); }
  }

  protected IndexReader in;

  public FilterIndexReader(IndexReader in) {
    super();
    this.in = in;
  }

  @Override
  public Directory directory() {
    ensureOpen();
    return in.directory();
  }
  
  @Override
  public IndexCommit getIndexCommit() throws IOException {
    ensureOpen();
    return in.getIndexCommit();
  }
  
  @Override
  public FieldInfos getFieldInfos() {
    return in.getFieldInfos();
  }

  @Override
  public int getTermInfosIndexDivisor() {
    ensureOpen();
    return in.getTermInfosIndexDivisor();
  }
  
  @Override
  public TermFreqVector[] getTermFreqVectors(int docNumber)
          throws IOException {
    ensureOpen();
    return in.getTermFreqVectors(docNumber);
  }

  @Override
  public TermFreqVector getTermFreqVector(int docNumber, String field)
          throws IOException {
    ensureOpen();
    return in.getTermFreqVector(docNumber, field);
  }

  @Override
  public void getTermFreqVector(int docNumber, String field, TermVectorMapper mapper) throws IOException {
    ensureOpen();
    in.getTermFreqVector(docNumber, field, mapper);
  }

  @Override
  public void getTermFreqVector(int docNumber, TermVectorMapper mapper) throws IOException {
    ensureOpen();
    in.getTermFreqVector(docNumber, mapper);
  }

  @Override
  public long getUniqueTermCount() throws IOException {
    ensureOpen();
    return in.getUniqueTermCount();
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
  public Document document(int n, FieldSelector fieldSelector) throws CorruptIndexException, IOException {
    ensureOpen();
    return in.document(n, fieldSelector);
  }

  @Override
  public boolean isDeleted(int n) {
    // Don't call ensureOpen() here (it could affect performance)
    return in.isDeleted(n);
  }

  @Override
  public boolean hasDeletions() {
    ensureOpen();
    return in.hasDeletions();
  }

  @Override @Deprecated
  protected void doUndeleteAll() throws CorruptIndexException, IOException {in.undeleteAll();}

  @Override
  public boolean hasNorms(String field) throws IOException {
    ensureOpen();
    return in.hasNorms(field);
  }

  @Override
  public byte[] norms(String f) throws IOException {
    ensureOpen();
    return in.norms(f);
  }

  @Override
  public void norms(String f, byte[] bytes, int offset) throws IOException {
    ensureOpen();
    in.norms(f, bytes, offset);
  }

  @Override @Deprecated
  protected void doSetNorm(int d, String f, byte b) throws CorruptIndexException, IOException {
    in.setNorm(d, f, b);
  }

  @Override
  public TermEnum terms() throws IOException {
    ensureOpen();
    return in.terms();
  }

  @Override
  public TermEnum terms(Term t) throws IOException {
    ensureOpen();
    return in.terms(t);
  }

  @Override
  public int docFreq(Term t) throws IOException {
    ensureOpen();
    return in.docFreq(t);
  }

  @Override
  public TermDocs termDocs() throws IOException {
    ensureOpen();
    return in.termDocs();
  }

  @Override
  public TermDocs termDocs(Term term) throws IOException {
    ensureOpen();
    return in.termDocs(term);
  }

  @Override
  public TermPositions termPositions() throws IOException {
    ensureOpen();
    return in.termPositions();
  }

  @Override @Deprecated
  protected void doDelete(int n) throws  CorruptIndexException, IOException { in.deleteDocument(n); }
  
  @Override @Deprecated
  protected void doCommit(Map<String,String> commitUserData) throws IOException {
    in.commit(commitUserData);
  }
  
  @Override
  protected void doClose() throws IOException {
    in.close();
  }

  @Override
  public long getVersion() {
    ensureOpen();
    return in.getVersion();
  }

  @Override
  public boolean isCurrent() throws CorruptIndexException, IOException {
    ensureOpen();
    return in.isCurrent();
  }

  @Deprecated
  @Override
  public boolean isOptimized() {
    ensureOpen();
    return in.isOptimized();
  }

  @Override
  public IndexReader[] getSequentialSubReaders() {
    return in.getSequentialSubReaders();
  }

  @Override
  public Map<String, String> getCommitUserData() { 
    return in.getCommitUserData();
  }
  

  @Override
  public Object getCoreCacheKey() {
    return in.getCoreCacheKey();
  }


  @Override
  public Object getDeletesCacheKey() {
    return in.getDeletesCacheKey();
  }

  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder("FilterReader(");
    buffer.append(in);
    buffer.append(')');
    return buffer.toString();
  }
}
