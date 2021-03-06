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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.search;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.AtomicReaderContext;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.Bits;
import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.ToStringUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ConstantScoreQuery extends Query {
  protected final Filter filter;
  protected final Query query;

  public ConstantScoreQuery(Query query) {
    if (query == null)
      throw new NullPointerException("Query may not be null");
    this.filter = null;
    this.query = query;
  }


  public ConstantScoreQuery(Filter filter) {
    if (filter == null)
      throw new NullPointerException("Filter may not be null");
    this.filter = filter;
    this.query = null;
  }

  public Filter getFilter() {
    return filter;
  }

  public Query getQuery() {
    return query;
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    if (query != null) {
      Query rewritten = query.rewrite(reader);
      if (rewritten != query) {
        rewritten = new ConstantScoreQuery(rewritten);
        rewritten.setBoost(this.getBoost());
        return rewritten;
      }
    } else {
      assert filter != null;
      // Fix outdated usage pattern from Lucene 2.x/early-3.x:
      // because ConstantScoreQuery only accepted filters,
      // QueryWrapperFilter was used to wrap queries.
      if (filter instanceof QueryWrapperFilter) {
        final QueryWrapperFilter qwf = (QueryWrapperFilter) filter;
        final Query rewritten = new ConstantScoreQuery(qwf.getQuery().rewrite(reader));
        rewritten.setBoost(this.getBoost());
        return rewritten;
      }
    }
    return this;
  }

  @Override
  public void extractTerms(Set<Term> terms) {
    // TODO: OK to not add any terms when wrapped a filter
    // and used with MultiSearcher, but may not be OK for
    // highlighting.
    // If a query was wrapped, we delegate to query.
    if (query != null)
      query.extractTerms(terms);
  }

  protected class ConstantWeight extends Weight {
    private final Weight innerWeight;
    private float queryNorm;
    private float queryWeight;
    
    public ConstantWeight(IndexSearcher searcher) throws IOException {
      this.innerWeight = (query == null) ? null : query.createWeight(searcher);
    }

    @Override
    public Query getQuery() {
      return ConstantScoreQuery.this;
    }

    @Override
    public float getValueForNormalization() throws IOException {
      // we calculate sumOfSquaredWeights of the inner weight, but ignore it (just to initialize everything)
      if (innerWeight != null) innerWeight.getValueForNormalization();
      queryWeight = getBoost();
      return queryWeight * queryWeight;
    }

    @Override
    public void normalize(float norm, float topLevelBoost) {
      this.queryNorm = norm * topLevelBoost;
      queryWeight *= this.queryNorm;
      // we normalize the inner weight, but ignore it (just to initialize everything)
      if (innerWeight != null) innerWeight.normalize(norm, topLevelBoost);
    }

    @Override
    public BulkScorer bulkScorer(AtomicReaderContext context, boolean scoreDocsInOrder, Bits acceptDocs) throws IOException {
      final DocIdSetIterator disi;
      if (filter != null) {
        assert query == null;
        return super.bulkScorer(context, scoreDocsInOrder, acceptDocs);
      } else {
        assert query != null && innerWeight != null;
        BulkScorer bulkScorer = innerWeight.bulkScorer(context, scoreDocsInOrder, acceptDocs);
        if (bulkScorer == null) {
          return null;
        }
        return new ConstantBulkScorer(bulkScorer, this, queryWeight);
      }
    }

    @Override
    public Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException {
      final DocIdSetIterator disi;
      if (filter != null) {
        assert query == null;
        final DocIdSet dis = filter.getDocIdSet(context, acceptDocs);
        if (dis == null) {
          return null;
        }
        disi = dis.iterator();
      } else {
        assert query != null && innerWeight != null;
        disi = innerWeight.scorer(context, acceptDocs);
      }

      if (disi == null) {
        return null;
      }
      return new ConstantScorer(disi, this, queryWeight);
    }

    @Override
    public boolean scoresDocsOutOfOrder() {
      return (innerWeight != null) ? innerWeight.scoresDocsOutOfOrder() : false;
    }

    @Override
    public Explanation explain(AtomicReaderContext context, int doc) throws IOException {
      final Scorer cs = scorer(context, context.reader().getLiveDocs());
      final boolean exists = (cs != null && cs.advance(doc) == doc);

      final ComplexExplanation result = new ComplexExplanation();
      if (exists) {
        result.setDescription(ConstantScoreQuery.this.toString() + ", product of:");
        result.setValue(queryWeight);
        result.setMatch(Boolean.TRUE);
        result.addDetail(new Explanation(getBoost(), "boost"));
        result.addDetail(new Explanation(queryNorm, "queryNorm"));
      } else {
        result.setDescription(ConstantScoreQuery.this.toString() + " doesn't match id " + doc);
        result.setValue(0);
        result.setMatch(Boolean.FALSE);
      }
      return result;
    }
  }


  protected class ConstantBulkScorer extends BulkScorer {
    final BulkScorer bulkScorer;
    final Weight weight;
    final float theScore;

    public ConstantBulkScorer(BulkScorer bulkScorer, Weight weight, float theScore) {
      this.bulkScorer = bulkScorer;
      this.weight = weight;
      this.theScore = theScore;
    }

    @Override
    public boolean score(Collector collector, int max) throws IOException {
      return bulkScorer.score(wrapCollector(collector), max);
    }

    private Collector wrapCollector(final Collector collector) {
      return new Collector() {
        @Override
        public void setScorer(Scorer scorer) throws IOException {
          // we must wrap again here, but using the scorer passed in as parameter:
          collector.setScorer(new ConstantScorer(scorer, weight, theScore));
        }
        
        @Override
        public void collect(int doc) throws IOException {
          collector.collect(doc);
        }
        
        @Override
        public void setNextReader(AtomicReaderContext context) throws IOException {
          collector.setNextReader(context);
        }
        
        @Override
        public boolean acceptsDocsOutOfOrder() {
          return collector.acceptsDocsOutOfOrder();
        }
      };
    }
  }

  protected class ConstantScorer extends Scorer {
    final DocIdSetIterator docIdSetIterator;
    final float theScore;

    public ConstantScorer(DocIdSetIterator docIdSetIterator, Weight w, float theScore) {
      super(w);
      this.theScore = theScore;
      this.docIdSetIterator = docIdSetIterator;
    }

    @Override
    public int nextDoc() throws IOException {
      return docIdSetIterator.nextDoc();
    }
    
    @Override
    public int docID() {
      return docIdSetIterator.docID();
    }

    @Override
    public float score() throws IOException {
      assert docIdSetIterator.docID() != NO_MORE_DOCS;
      return theScore;
    }

    @Override
    public int freq() throws IOException {
      return 1;
    }

    @Override
    public int advance(int target) throws IOException {
      return docIdSetIterator.advance(target);
    }
    
    @Override
    public long cost() {
      return docIdSetIterator.cost();
    }

    @Override
    public Collection<ChildScorer> getChildren() {
      if (query != null) {
        return Collections.singletonList(new ChildScorer((Scorer) docIdSetIterator, "constant"));
      } else {
        return Collections.emptyList();
      }
    }
  }

  @Override
  public Weight createWeight(IndexSearcher searcher) throws IOException {
    return new ConstantScoreQuery.ConstantWeight(searcher);
  }

  @Override
  public String toString(String field) {
    return new StringBuilder("ConstantScore(")
      .append((query == null) ? filter.toString() : query.toString(field))
      .append(')')
      .append(ToStringUtils.boost(getBoost()))
      .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o))
      return false;
    if (o instanceof ConstantScoreQuery) {
      final ConstantScoreQuery other = (ConstantScoreQuery) o;
      return 
        ((this.filter == null) ? other.filter == null : this.filter.equals(other.filter)) &&
        ((this.query == null) ? other.query == null : this.query.equals(other.query));
    }
    return false;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() +
      ((query == null) ? filter : query).hashCode();
  }

}
