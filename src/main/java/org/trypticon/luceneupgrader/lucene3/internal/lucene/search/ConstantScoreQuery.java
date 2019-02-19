package org.trypticon.luceneupgrader.lucene3.internal.lucene.search;

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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.IndexReader;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.index.Term;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.ToStringUtils;

import java.io.IOException;
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
    private final Similarity similarity;
    private float queryNorm;
    private float queryWeight;
    
    public ConstantWeight(Searcher searcher) throws IOException {
      this.similarity = getSimilarity(searcher);
      this.innerWeight = (query == null) ? null : query.createWeight(searcher);
    }

    @Override
    public Query getQuery() {
      return ConstantScoreQuery.this;
    }

    @Override
    public float getValue() {
      return queryWeight;
    }

    @Override
    public float sumOfSquaredWeights() throws IOException {
      // we calculate sumOfSquaredWeights of the inner weight, but ignore it (just to initialize everything)
      if (innerWeight != null) innerWeight.sumOfSquaredWeights();
      queryWeight = getBoost();
      return queryWeight * queryWeight;
    }

    @Override
    public void normalize(float norm) {
      this.queryNorm = norm;
      queryWeight *= this.queryNorm;
      // we normalize the inner weight, but ignore it (just to initialize everything)
      if (innerWeight != null) innerWeight.normalize(norm);
    }

    @Override
    public Scorer scorer(IndexReader reader, boolean scoreDocsInOrder, boolean topScorer) throws IOException {
      final DocIdSetIterator disi;
      if (filter != null) {
        assert query == null;
        final DocIdSet dis = filter.getDocIdSet(reader);
        if (dis == null)
          return null;
        disi = dis.iterator();
      } else {
        assert query != null && innerWeight != null;
        disi =
          innerWeight.scorer(reader, scoreDocsInOrder, topScorer);
      }
      if (disi == null)
        return null;
      return new ConstantScorer(similarity, disi, this);
    }
    
    @Override
    public boolean scoresDocsOutOfOrder() {
      return (innerWeight != null) ? innerWeight.scoresDocsOutOfOrder() : false;
    }

    @Override
    public Explanation explain(IndexReader reader, int doc) throws IOException {
      final Scorer cs = scorer(reader, true, false);
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

  protected class ConstantScorer extends Scorer {
    final DocIdSetIterator docIdSetIterator;
    final float theScore;

    public ConstantScorer(Similarity similarity, DocIdSetIterator docIdSetIterator, Weight w) throws IOException {
      super(similarity,w);
      theScore = w.getValue();
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
    public int advance(int target) throws IOException {
      return docIdSetIterator.advance(target);
    }
    
    private Collector wrapCollector(final Collector collector) {
      return new Collector() {
        @Override
        public void setScorer(Scorer scorer) throws IOException {
          // we must wrap again here, but using the scorer passed in as parameter:
          collector.setScorer(new ConstantScorer(ConstantScorer.this.getSimilarity(),
            scorer, ConstantScorer.this.weight));
        }
        
        @Override
        public void collect(int doc) throws IOException {
          collector.collect(doc);
        }
        
        @Override
        public void setNextReader(IndexReader reader, int docBase) throws IOException {
          collector.setNextReader(reader, docBase);
        }
        
        @Override
        public boolean acceptsDocsOutOfOrder() {
          return collector.acceptsDocsOutOfOrder();
        }
      };
    }

    // this optimization allows out of order scoring as top scorer!
    @Override
    public void score(Collector collector) throws IOException {
      if (docIdSetIterator instanceof Scorer) {
        ((Scorer) docIdSetIterator).score(wrapCollector(collector));
      } else {
        super.score(collector);
      }
    }

    // this optimization allows out of order scoring as top scorer,
    // TODO: theoretically this method should not be called because its protected and
    // this class does not use it, it should be public in Scorer!
    @Override
    protected boolean score(Collector collector, int max, int firstDocID) throws IOException {
      if (docIdSetIterator instanceof Scorer) {
        return ((Scorer) docIdSetIterator).score(wrapCollector(collector), max, firstDocID);
      } else {
        return super.score(collector, max, firstDocID);
      }
    }
  }

  @Override
  public Weight createWeight(Searcher searcher) throws IOException {
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
