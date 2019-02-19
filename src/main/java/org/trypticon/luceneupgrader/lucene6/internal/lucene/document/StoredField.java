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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.document;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.IndexReader; // javadocs
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.IndexSearcher; // javadocs
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.BytesRef;


public class StoredField extends Field {

  public final static FieldType TYPE;
  static {
    TYPE = new FieldType();
    TYPE.setStored(true);
    TYPE.freeze();
  }

  protected StoredField(String name, FieldType type) {
    super(name, type);
  }
  
  public StoredField(String name, BytesRef bytes, FieldType type) {
    super(name, bytes, type);
  }
  
  public StoredField(String name, byte[] value) {
    super(name, value, TYPE);
  }
  
  public StoredField(String name, byte[] value, int offset, int length) {
    super(name, value, offset, length, TYPE);
  }

  public StoredField(String name, BytesRef value) {
    super(name, value, TYPE);
  }

  public StoredField(String name, String value) {
    super(name, value, TYPE);
  }
  
  public StoredField(String name, String value, FieldType type) {
    super(name, value, type);
  }

  // TODO: not great but maybe not a big problem?
  public StoredField(String name, int value) {
    super(name, TYPE);
    fieldsData = value;
  }

  public StoredField(String name, float value) {
    super(name, TYPE);
    fieldsData = value;
  }

  public StoredField(String name, long value) {
    super(name, TYPE);
    fieldsData = value;
  }

  public StoredField(String name, double value) {
    super(name, TYPE);
    fieldsData = value;
  }
}
