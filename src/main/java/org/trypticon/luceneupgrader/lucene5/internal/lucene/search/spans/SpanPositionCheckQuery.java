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
package org.trypticon.luceneupgrader.lucene5.internal.lucene.search.spans;


import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.index.TermContext;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene5.internal.lucene.search.spans.FilterSpans.AcceptStatus;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;



public abstract class SpanPositionCheckQuery extends SpanQuery implements Cloneable {
  protected SpanQuery match;

  public SpanPositionCheckQuery(SpanQuery match) {
    this.match = Objects.requireNonNull(match);
  }


  public SpanQuery getMatch() { return match; }

  @Override
  public String getField() { return match.getField(); }

  protected abstract AcceptStatus acceptPosition(Spans spans) throws IOException;

  @Override
  public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    SpanWeight matchWeight = match.createWeight(searcher, false);
    return new SpanPositionCheckWeight(matchWeight, searcher, needsScores ? getTermContexts(matchWeight) : null);
  }

  public class SpanPositionCheckWeight extends SpanWeight {

    final SpanWeight matchWeight;

    public SpanPositionCheckWeight(SpanWeight matchWeight, IndexSearcher searcher, Map<Term, TermContext> terms)
        throws IOException {
      super(SpanPositionCheckQuery.this, searcher, terms);
      this.matchWeight = matchWeight;
    }

    @Override
    public void extractTerms(Set<Term> terms) {
      matchWeight.extractTerms(terms);
    }

    @Override
    public void extractTermContexts(Map<Term, TermContext> contexts) {
      matchWeight.extractTermContexts(contexts);
    }

    @Override
    public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
      Spans matchSpans = matchWeight.getSpans(context, requiredPostings);
      return (matchSpans == null) ? null : new FilterSpans(matchSpans) {
        @Override
        protected AcceptStatus accept(Spans candidate) throws IOException {
          return acceptPosition(candidate);
        }
      };
    }

  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (getBoost() != 1f) {
      return super.rewrite(reader);
    }
    SpanQuery rewritten = (SpanQuery) match.rewrite(reader);
    if (rewritten != match) {
      SpanPositionCheckQuery clone = (SpanPositionCheckQuery) this.clone();
      clone.match = rewritten;
      return clone;
    }

    return super.rewrite(reader);
  }

  @Override
  public boolean equals(Object o) {
    if (! super.equals(o)) {
      return false;
    }
    SpanPositionCheckQuery spcq = (SpanPositionCheckQuery) o;
    return match.equals(spcq.match);
  }

  @Override
  public int hashCode() {
    return match.hashCode() ^ super.hashCode();
  }
}
