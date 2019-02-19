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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities;

import static org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.SimilarityBase.log2;

public class BasicModelP extends BasicModel {
  protected static double LOG2_E = log2(Math.E);
  
  public BasicModelP() {}
  
  @Override
  public final float score(BasicStats stats, float tfn) {
    float lambda = (float)(stats.getTotalTermFreq()+1) / (stats.getNumberOfDocuments()+1);
    return (float)(tfn * log2(tfn / lambda)
        + (lambda + 1 / (12 * tfn) - tfn) * LOG2_E
        + 0.5 * log2(2 * Math.PI * tfn));
  }

  @Override
  public String toString() {
    return "P";
  }
}
