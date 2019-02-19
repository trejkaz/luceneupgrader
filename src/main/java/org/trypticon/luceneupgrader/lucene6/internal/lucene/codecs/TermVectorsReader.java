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
package org.trypticon.luceneupgrader.lucene6.internal.lucene.codecs;


import java.io.Closeable;
import java.io.IOException;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.analysis.tokenattributes.OffsetAttribute; // javadocs
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.Fields;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.Accountable;

public abstract class TermVectorsReader implements Cloneable, Closeable, Accountable {

  protected TermVectorsReader() {
  }


  public abstract Fields get(int doc) throws IOException;
  

  public abstract void checkIntegrity() throws IOException;
  
  @Override
  public abstract TermVectorsReader clone();
  

  public TermVectorsReader getMergeInstance() throws IOException {
    return this;
  }
}
