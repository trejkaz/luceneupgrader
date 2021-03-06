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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.lucene41;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.*;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.blocktree.BlockTreeTermsReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.codecs.blocktree.BlockTreeTermsWriter;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.IOUtils;

import java.io.IOException;

public final class Lucene41PostingsFormat extends PostingsFormat {
  public static final String DOC_EXTENSION = "doc";

  public static final String POS_EXTENSION = "pos";

  public static final String PAY_EXTENSION = "pay";

  private final int minTermBlockSize;
  private final int maxTermBlockSize;

  // NOTE: must be multiple of 64 because of PackedInts long-aligned encoding/decoding
  public final static int BLOCK_SIZE = 128;

  public Lucene41PostingsFormat() {
    this(BlockTreeTermsWriter.DEFAULT_MIN_BLOCK_SIZE, BlockTreeTermsWriter.DEFAULT_MAX_BLOCK_SIZE);
  }


  public Lucene41PostingsFormat(int minTermBlockSize, int maxTermBlockSize) {
    super("Lucene41");
    this.minTermBlockSize = minTermBlockSize;
    assert minTermBlockSize > 1;
    this.maxTermBlockSize = maxTermBlockSize;
    assert minTermBlockSize <= maxTermBlockSize;
  }

  @Override
  public String toString() {
    return getName() + "(blocksize=" + BLOCK_SIZE + ")";
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    PostingsWriterBase postingsWriter = new Lucene41PostingsWriter(state);

    boolean success = false;
    try {
      FieldsConsumer ret = new BlockTreeTermsWriter(state, 
                                                    postingsWriter,
                                                    minTermBlockSize, 
                                                    maxTermBlockSize);
      success = true;
      return ret;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(postingsWriter);
      }
    }
  }

  @Override
  public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
    PostingsReaderBase postingsReader = new Lucene41PostingsReader(state.directory,
                                                                state.fieldInfos,
                                                                state.segmentInfo,
                                                                state.context,
                                                                state.segmentSuffix);
    boolean success = false;
    try {
      FieldsProducer ret = new BlockTreeTermsReader(state.directory,
                                                    state.fieldInfos,
                                                    state.segmentInfo,
                                                    postingsReader,
                                                    state.context,
                                                    state.segmentSuffix,
                                                    state.termsIndexDivisor);
      success = true;
      return ret;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(postingsReader);
      }
    }
  }
}
