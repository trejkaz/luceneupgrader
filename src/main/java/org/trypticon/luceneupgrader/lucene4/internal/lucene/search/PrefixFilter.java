package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;

public class PrefixFilter extends MultiTermQueryWrapperFilter<PrefixQuery> {

  public PrefixFilter(Term prefix) {
    super(new PrefixQuery(prefix));
  }

  public Term getPrefix() { return query.getPrefix(); }

  @Override
  public String toString () {
    StringBuilder buffer = new StringBuilder();
    buffer.append("PrefixFilter(");
    buffer.append(getPrefix().toString());
    buffer.append(")");
    return buffer.toString();
  }

}



