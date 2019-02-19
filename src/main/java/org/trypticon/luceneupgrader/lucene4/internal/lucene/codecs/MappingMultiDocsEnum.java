package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs;

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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.DocsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MergeState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MultiDocsEnum;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.MultiDocsEnum.EnumWithSlice;

import java.io.IOException;

public final class MappingMultiDocsEnum extends DocsEnum {
  private MultiDocsEnum.EnumWithSlice[] subs;
  int numSubs;
  int upto;
  MergeState.DocMap currentMap;
  DocsEnum current;
  int currentBase;
  int doc = -1;
  private MergeState mergeState;

  public MappingMultiDocsEnum() {
  }

  MappingMultiDocsEnum reset(MultiDocsEnum docsEnum) {
    this.numSubs = docsEnum.getNumSubs();
    this.subs = docsEnum.getSubs();
    upto = -1;
    current = null;
    return this;
  }

  public void setMergeState(MergeState mergeState) {
    this.mergeState = mergeState;
  }
  
  public int getNumSubs() {
    return numSubs;
  }

  public EnumWithSlice[] getSubs() {
    return subs;
  }

  @Override
  public int freq() throws IOException {
    return current.freq();
  }

  @Override
  public int docID() {
    return doc;
  }

  @Override
  public int advance(int target) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int nextDoc() throws IOException {
    while(true) {
      if (current == null) {
        if (upto == numSubs-1) {
          return this.doc = NO_MORE_DOCS;
        } else {
          upto++;
          final int reader = subs[upto].slice.readerIndex;
          current = subs[upto].docsEnum;
          currentBase = mergeState.docBase[reader];
          currentMap = mergeState.docMaps[reader];
          assert currentMap.maxDoc() == subs[upto].slice.length: "readerIndex=" + reader + " subs.len=" + subs.length + " len1=" + currentMap.maxDoc() + " vs " + subs[upto].slice.length;
        }
      }

      int doc = current.nextDoc();
      if (doc != NO_MORE_DOCS) {
        // compact deletions
        doc = currentMap.get(doc);
        if (doc == -1) {
          continue;
        }
        return this.doc = currentBase + doc;
      } else {
        current = null;
      }
    }
  }
  
  @Override
  public long cost() {
    long cost = 0;
    for (EnumWithSlice enumWithSlice : subs) {
      cost += enumWithSlice.docsEnum.cost();
    }
    return cost;
  }
}

