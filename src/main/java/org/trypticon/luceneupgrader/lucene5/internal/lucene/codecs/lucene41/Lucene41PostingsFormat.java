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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene41;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.FieldsConsumer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.FieldsProducer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.PostingsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.PostingsReaderBase;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.blocktree.Lucene40BlockTreeTermsReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentReadState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentWriteState;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.util.IOUtils;

@Deprecated
public class Lucene41PostingsFormat extends PostingsFormat {
  public static final String DOC_EXTENSION = "doc";

  public static final String POS_EXTENSION = "pos";

  public static final String PAY_EXTENSION = "pay";
  

  static final int maxSkipLevels = 10;

  final static String TERMS_CODEC = "Lucene41PostingsWriterTerms";
  final static String DOC_CODEC = "Lucene41PostingsWriterDoc";
  final static String POS_CODEC = "Lucene41PostingsWriterPos";
  final static String PAY_CODEC = "Lucene41PostingsWriterPay";

  // Increment version to change it
  final static int VERSION_START = 0;
  final static int VERSION_META_ARRAY = 1;
  final static int VERSION_CHECKSUM = 2;
  final static int VERSION_CURRENT = VERSION_CHECKSUM;

  // NOTE: must be multiple of 64 because of PackedInts long-aligned encoding/decoding
  public final static int BLOCK_SIZE = 128;

  public Lucene41PostingsFormat() {
    super("Lucene41");
  }

  @Override
  public String toString() {
    return getName() + "(blocksize=" + BLOCK_SIZE + ")";
  }

  @Override
  public FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
    throw new UnsupportedOperationException("this codec can only be used for reading");
  }

  @Override
  public final FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
    PostingsReaderBase postingsReader = new Lucene41PostingsReader(state.directory,
                                                                state.fieldInfos,
                                                                state.segmentInfo,
                                                                state.context,
                                                                state.segmentSuffix);
    boolean success = false;
    try {
      FieldsProducer ret = new Lucene40BlockTreeTermsReader(postingsReader, state);
      success = true;
      return ret;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(postingsReader);
      }
    }
  }
}
