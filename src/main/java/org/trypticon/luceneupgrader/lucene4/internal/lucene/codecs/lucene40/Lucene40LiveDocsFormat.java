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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene40;

import java.io.IOException;
import java.util.Collection;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.CodecUtil;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.CorruptIndexException;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexFileNames;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentCommitInfo;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.DataOutput; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.MutableBits;

public class Lucene40LiveDocsFormat extends LiveDocsFormat {

  static final String DELETES_EXTENSION = "del";

  public Lucene40LiveDocsFormat() {
  }
  
  @Override
  public MutableBits newLiveDocs(int size) throws IOException {
    BitVector bitVector = new BitVector(size);
    bitVector.invertAll();
    return bitVector;
  }

  @Override
  public MutableBits newLiveDocs(Bits existing) throws IOException {
    final BitVector liveDocs = (BitVector) existing;
    return liveDocs.clone();
  }

  @Override
  public Bits readLiveDocs(Directory dir, SegmentCommitInfo info, IOContext context) throws IOException {
    String filename = IndexFileNames.fileNameFromGeneration(info.info.name, DELETES_EXTENSION, info.getDelGen());
    final BitVector liveDocs = new BitVector(dir, filename, context);
    if (liveDocs.length() != info.info.getDocCount()) {
      throw new CorruptIndexException("liveDocs.length()=" + liveDocs.length() + "info.docCount=" + info.info.getDocCount() + " (filename=" + filename + ")");
    }
    if (liveDocs.count() != info.info.getDocCount() - info.getDelCount()) {
      throw new CorruptIndexException("liveDocs.count()=" + liveDocs.count() + " info.docCount=" + info.info.getDocCount() + " info.getDelCount()=" + info.getDelCount() + " (filename=" + filename + ")");
    }
    return liveDocs;
  }

  @Override
  public void writeLiveDocs(MutableBits bits, Directory dir, SegmentCommitInfo info, int newDelCount, IOContext context) throws IOException {
    String filename = IndexFileNames.fileNameFromGeneration(info.info.name, DELETES_EXTENSION, info.getNextDelGen());
    final BitVector liveDocs = (BitVector) bits;
    assert liveDocs.count() == info.info.getDocCount() - info.getDelCount() - newDelCount;
    assert liveDocs.length() == info.info.getDocCount();
    liveDocs.write(dir, filename, context);
  }

  @Override
  public void files(SegmentCommitInfo info, Collection<String> files) throws IOException {
    if (info.hasDeletions()) {
      files.add(IndexFileNames.fileNameFromGeneration(info.info.name, DELETES_EXTENSION, info.getDelGen()));
    }
  }
}
