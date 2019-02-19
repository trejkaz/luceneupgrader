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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.PostingsFormat; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.perfield.PerFieldPostingsFormat; // javadocs
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.store.IOContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.InfoStream;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.MutableBits;

public class SegmentWriteState {

  public final InfoStream infoStream;

  public final Directory directory;

  public final SegmentInfo segmentInfo;

  public final FieldInfos fieldInfos;

  public int delCountOnFlush;

  public final BufferedUpdates segUpdates;

  public MutableBits liveDocs;


  public final String segmentSuffix;


  public int termIndexInterval;                   // TODO: this should be private to the codec, not settable here or in IWC
  
  public final IOContext context;

  public SegmentWriteState(InfoStream infoStream, Directory directory, SegmentInfo segmentInfo, FieldInfos fieldInfos,
      int termIndexInterval, BufferedUpdates segUpdates, IOContext context) {
    this(infoStream, directory, segmentInfo, fieldInfos, termIndexInterval, segUpdates, context, "");
  }

  public SegmentWriteState(InfoStream infoStream, Directory directory, SegmentInfo segmentInfo, FieldInfos fieldInfos,
      int termIndexInterval, BufferedUpdates segUpdates, IOContext context, String segmentSuffix) {
    this.infoStream = infoStream;
    this.segUpdates = segUpdates;
    this.directory = directory;
    this.segmentInfo = segmentInfo;
    this.fieldInfos = fieldInfos;
    this.termIndexInterval = termIndexInterval;
    assert assertSegmentSuffix(segmentSuffix);
    this.segmentSuffix = segmentSuffix;
    this.context = context;
  }
  
  public SegmentWriteState(SegmentWriteState state, String segmentSuffix) {
    infoStream = state.infoStream;
    directory = state.directory;
    segmentInfo = state.segmentInfo;
    fieldInfos = state.fieldInfos;
    termIndexInterval = state.termIndexInterval;
    context = state.context;
    this.segmentSuffix = segmentSuffix;
    segUpdates = state.segUpdates;
    delCountOnFlush = state.delCountOnFlush;
  }
  
  // currently only used by assert? clean up and make real check?
  // either its a segment suffix (_X_Y) or its a parseable generation
  // TODO: this is very confusing how ReadersAndUpdates passes generations via
  // this mechanism, maybe add 'generation' explicitly to ctor create the 'actual suffix' here?
  private boolean assertSegmentSuffix(String segmentSuffix) {
    assert segmentSuffix != null;
    if (!segmentSuffix.isEmpty()) {
      int numParts = segmentSuffix.split("_").length;
      if (numParts == 2) {
        return true;
      } else if (numParts == 1) {
        Long.parseLong(segmentSuffix, Character.MAX_RADIX);
        return true;
      }
      return false; // invalid
    }
    return true;
  }
}
