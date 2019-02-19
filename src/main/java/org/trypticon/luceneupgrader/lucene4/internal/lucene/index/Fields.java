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
import java.util.Iterator;

public abstract class Fields implements Iterable<String> {

  protected Fields() {
  }

  @Override
  public abstract Iterator<String> iterator();

  public abstract Terms terms(String field) throws IOException;


  public abstract int size();
  

  @Deprecated
  public long getUniqueTermCount() throws IOException {
    long numTerms = 0;
    for (String field : this) {
      Terms terms = terms(field);
      if (terms != null) {
        final long termCount = terms.size();
        if (termCount == -1) {
          return -1;
        }
          
        numTerms += termCount;
      }
    }
    return numTerms;
  }

  public final static Fields[] EMPTY_ARRAY = new Fields[0];
}
