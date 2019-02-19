package org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities;

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

import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.Explanation;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.search.similarities.Normalization.NoNormalization;

public class IBSimilarity extends SimilarityBase {
  protected final Distribution distribution;
  protected final Lambda lambda;
  protected final Normalization normalization;
  
  public IBSimilarity(Distribution distribution,
                      Lambda lambda,
                      Normalization normalization) {
    this.distribution = distribution;
    this.lambda = lambda;
    this.normalization = normalization;
  }
  
  @Override
  protected float score(BasicStats stats, float freq, float docLen) {
    return stats.getTotalBoost() *
        distribution.score(
            stats,
            normalization.tfn(stats, freq, docLen),
            lambda.lambda(stats));
  }

  @Override
  protected void explain(
      Explanation expl, BasicStats stats, int doc, float freq, float docLen) {
    if (stats.getTotalBoost() != 1.0f) {
      expl.addDetail(new Explanation(stats.getTotalBoost(), "boost"));
    }
    Explanation normExpl = normalization.explain(stats, freq, docLen);
    Explanation lambdaExpl = lambda.explain(stats);
    expl.addDetail(normExpl);
    expl.addDetail(lambdaExpl);
    expl.addDetail(distribution.explain(
        stats, normExpl.getValue(), lambdaExpl.getValue()));
  }
  
  @Override
  public String toString() {
    return "IB " + distribution.toString() + "-" + lambda.toString()
                 + normalization.toString();
  }
  
  public Distribution getDistribution() {
    return distribution;
  }
  
  public Lambda getLambda() {
    return lambda;
  }

  public Normalization getNormalization() {
    return normalization;
  }
}
