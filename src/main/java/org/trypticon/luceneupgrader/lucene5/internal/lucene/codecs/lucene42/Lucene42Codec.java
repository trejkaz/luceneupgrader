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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene42;

import java.io.IOException;

import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.Codec;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.CompoundFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.DocValuesFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.FieldInfosFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.NormsConsumer;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.NormsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.PostingsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.TermVectorsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene40.Lucene40CompoundFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene40.Lucene40LiveDocsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene40.Lucene40SegmentInfoFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.lucene41.Lucene41StoredFieldsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.perfield.PerFieldDocValuesFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.SegmentWriteState;

@Deprecated
public class Lucene42Codec extends Codec {
  private final StoredFieldsFormat fieldsFormat = new Lucene41StoredFieldsFormat();
  private final TermVectorsFormat vectorsFormat = new Lucene42TermVectorsFormat();
  private final FieldInfosFormat fieldInfosFormat = new Lucene42FieldInfosFormat();
  private final SegmentInfoFormat infosFormat = new Lucene40SegmentInfoFormat();
  private final LiveDocsFormat liveDocsFormat = new Lucene40LiveDocsFormat();
  private final CompoundFormat compoundFormat = new Lucene40CompoundFormat();
  
  private final PostingsFormat postingsFormat = new PerFieldPostingsFormat() {
    @Override
    public PostingsFormat getPostingsFormatForField(String field) {
      return Lucene42Codec.this.getPostingsFormatForField(field);
    }
  };
  
  
  private final DocValuesFormat docValuesFormat = new PerFieldDocValuesFormat() {
    @Override
    public DocValuesFormat getDocValuesFormatForField(String field) {
      return Lucene42Codec.this.getDocValuesFormatForField(field);
    }
  };

  public Lucene42Codec() {
    super("Lucene42");
  }
  
  @Override
  public StoredFieldsFormat storedFieldsFormat() {
    return fieldsFormat;
  }
  
  @Override
  public TermVectorsFormat termVectorsFormat() {
    return vectorsFormat;
  }

  @Override
  public final PostingsFormat postingsFormat() {
    return postingsFormat;
  }
  
  @Override
  public FieldInfosFormat fieldInfosFormat() {
    return fieldInfosFormat;
  }
  
  @Override
  public SegmentInfoFormat segmentInfoFormat() {
    return infosFormat;
  }
  
  @Override
  public final LiveDocsFormat liveDocsFormat() {
    return liveDocsFormat;
  }
  
  @Override
  public CompoundFormat compoundFormat() {
    return compoundFormat;
  }


  public PostingsFormat getPostingsFormatForField(String field) {
    return defaultFormat;
  }
  

  public DocValuesFormat getDocValuesFormatForField(String field) {
    return defaultDVFormat;
  }
  
  @Override
  public final DocValuesFormat docValuesFormat() {
    return docValuesFormat;
  }

  private final PostingsFormat defaultFormat = PostingsFormat.forName("Lucene41");
  private final DocValuesFormat defaultDVFormat = DocValuesFormat.forName("Lucene42");

  private final NormsFormat normsFormat = new Lucene42NormsFormat() {
    @Override
    public NormsConsumer normsConsumer(SegmentWriteState state) throws IOException {
      throw new UnsupportedOperationException("this codec can only be used for reading");
    }
  };

  @Override
  public NormsFormat normsFormat() {
    return normsFormat;
  }
}
