package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.function;

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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.FieldCache;

public abstract class FieldCacheSource extends ValueSource {
  private String field;

  public FieldCacheSource(String field) {
    this.field=field;
  }

  /* (non-Javadoc) @see org.apache.lucene.search.function.ValueSource#getValues(org.apache.lucene.index.IndexReader) */
  @Override
  public final DocValues getValues(IndexReader reader) throws IOException {
    return getCachedFieldValues(FieldCache.DEFAULT, field, reader);
  }

  /* (non-Javadoc) @see org.apache.lucene.search.function.ValueSource#description() */
  @Override
  public String description() {
    return field;
  }

  public abstract DocValues getCachedFieldValues(FieldCache cache, String field, IndexReader reader) throws IOException;

  /*(non-Javadoc) @see java.lang.Object#equals(java.lang.Object) */
  @Override
  public final boolean equals(Object o) {
    if (!(o instanceof FieldCacheSource)) {
      return false;
    }
    FieldCacheSource other = (FieldCacheSource) o;
    return 
      this.field.equals(other.field) && 
      cachedFieldSourceEquals(other);
  }

  /*(non-Javadoc) @see java.lang.Object#hashCode() */
  @Override
  public final int hashCode() {
    return 
      field.hashCode() +
      cachedFieldSourceHashCode();
  }

  public abstract boolean cachedFieldSourceEquals(FieldCacheSource other);

  public abstract int cachedFieldSourceHashCode();
}
