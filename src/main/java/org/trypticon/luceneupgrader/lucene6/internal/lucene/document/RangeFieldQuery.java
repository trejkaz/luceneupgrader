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

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.FieldInfo;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReader;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.LeafReaderContext;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PointValues;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PointValues.Relation;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.index.PointValues.IntersectVisitor;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.ConstantScoreScorer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.ConstantScoreWeight;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSet;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.DocIdSetIterator;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.IndexSearcher;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Query;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Scorer;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.search.Weight;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.DocIdSetBuilder;
import org.trypticon.luceneupgrader.lucene6.internal.lucene.util.StringHelper;

abstract class RangeFieldQuery extends Query {
  final String field;

  final QueryType queryType;
  final int numDims;
  final byte[] ranges;
  final int bytesPerDim;

  enum QueryType {
    INTERSECTS,
    WITHIN,
    CONTAINS,
    CROSSES
  }

  RangeFieldQuery(String field, final byte[] ranges, final int numDims, final QueryType queryType) {
    checkArgs(field, ranges, numDims);
    if (queryType == null) {
      throw new IllegalArgumentException("Query type cannot be null");
    }
    this.field = field;
    this.queryType = queryType;
    this.numDims = numDims;
    this.ranges = ranges;
    this.bytesPerDim = ranges.length / (2*numDims);
  }

  private static void checkArgs(String field, final byte[] ranges, final int numDims) {
    if (field == null) {
      throw new IllegalArgumentException("field must not be null");
    }
    if (numDims > 4) {
      throw new IllegalArgumentException("dimension size cannot be greater than 4");
    }
    if (ranges == null || ranges.length == 0) {
      throw new IllegalArgumentException("encoded ranges cannot be null or empty");
    }
  }

  private void checkFieldInfo(FieldInfo fieldInfo) {
    if (fieldInfo.getPointDimensionCount()/2 != numDims) {
      throw new IllegalArgumentException("field=\"" + field + "\" was indexed with numDims="
          + fieldInfo.getPointDimensionCount()/2 + " but this query has numDims=" + numDims);
    }
  }

  @Override
  public final Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
    return new ConstantScoreWeight(this) {
      final RangeFieldComparator target = new RangeFieldComparator();

      private DocIdSet buildMatchingDocIdSet(LeafReader reader, PointValues values) throws IOException {
        DocIdSetBuilder result = new DocIdSetBuilder(reader.maxDoc(), values, field);
        values.intersect(field,
            new IntersectVisitor() {
              DocIdSetBuilder.BulkAdder adder;
              @Override
              public void grow(int count) {
                adder = result.grow(count);
              }
              @Override
              public void visit(int docID) throws IOException {
                adder.add(docID);
              }
              @Override
              public void visit(int docID, byte[] leaf) throws IOException {
                if (target.matches(leaf)) {
                  adder.add(docID);
                }
              }
              @Override
              public Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
                return compareRange(minPackedValue, maxPackedValue);
              }
            });
        return result.build();
      }

      private Relation compareRange(byte[] minPackedValue, byte[] maxPackedValue) {
        byte[] node = getInternalRange(minPackedValue, maxPackedValue);
        // compute range relation for BKD traversal
        if (target.intersects(node) == false) {
          return Relation.CELL_OUTSIDE_QUERY;
        } else if (target.within(node)) {
          // target within cell; continue traversing:
          return Relation.CELL_CROSSES_QUERY;
        } else if (target.contains(node)) {
          // target contains cell; add iff queryType is not a CONTAINS or CROSSES query:
          return (queryType == QueryType.CONTAINS || queryType == QueryType.CROSSES) ?
              Relation.CELL_OUTSIDE_QUERY : Relation.CELL_INSIDE_QUERY;
        }
        // target intersects cell; continue traversing:
        return Relation.CELL_CROSSES_QUERY;
      }

      @Override
      public Scorer scorer(LeafReaderContext context) throws IOException {
        LeafReader reader = context.reader();
        PointValues values = reader.getPointValues();
        if (values == null) {
          // no docs in this segment indexed any ranges
          return null;
        }
        FieldInfo fieldInfo = reader.getFieldInfos().fieldInfo(field);
        if (fieldInfo == null) {
          // no docs in this segment indexed this field
          return null;
        }
        checkFieldInfo(fieldInfo);
        boolean allDocsMatch = false;
        if (values.getDocCount(field) == reader.maxDoc()
            && compareRange(values.getMinPackedValue(field), values.getMaxPackedValue(field)) == Relation.CELL_INSIDE_QUERY) {
          allDocsMatch = true;
        }

        DocIdSetIterator iterator = allDocsMatch == true ?
            DocIdSetIterator.all(reader.maxDoc()) : buildMatchingDocIdSet(reader, values).iterator();
        return new ConstantScoreScorer(this, score(), iterator);
      }

      private byte[] getInternalRange(byte[] min, byte[] max) {
        byte[] range = new byte[min.length];
        final int dimSize = numDims * bytesPerDim;
        System.arraycopy(min, 0, range, 0, dimSize);
        System.arraycopy(max, dimSize, range, dimSize, dimSize);
        return range;
      }
    };
  }

  class RangeFieldComparator {
    final Predicate<byte[]> predicate;

    RangeFieldComparator() {
      switch (queryType) {
        case INTERSECTS:
          predicate = this::intersects;
          break;
        case WITHIN:
          predicate = this::contains;
          break;
        case CONTAINS:
          predicate = this::within;
          break;
        case CROSSES:
          // crosses first checks intersection (disjoint automatic fails),
          // then ensures the query doesn't wholly contain the leaf:
          predicate = (byte[] leaf) -> this.intersects(leaf)
              && this.contains(leaf) == false;
          break;
        default:
          throw new IllegalArgumentException("invalid queryType [" + queryType + "] found.");
      }
    }

    private boolean matches(final byte[] candidate) {
      return (Arrays.equals(ranges, candidate) && queryType != QueryType.CROSSES)
          || predicate.test(candidate);
    }

    private boolean intersects(final byte[] candidate) {
      return relate((int d) -> compareMinMax(candidate, d) > 0 || compareMaxMin(candidate, d) < 0);
    }

    private boolean within(final byte[] candidate) {
      return relate((int d) -> compareMinMin(candidate, d) < 0 || compareMaxMax(candidate, d) > 0);
    }

    private boolean contains(final byte[] candidate) {
      return relate((int d) -> compareMinMin(candidate, d) > 0 || compareMaxMax(candidate, d) < 0);
    }

    private boolean relate(IntPredicate predicate) {
      for (int d=0; d<numDims; ++d) {
        if (predicate.test(d)) {
          return false;
        }
      }
      return true;
    }

    private int compareMinMin(byte[] b, int dimension) {
      // convert dimension to offset:
      dimension *= bytesPerDim;
      return StringHelper.compare(bytesPerDim, ranges, dimension, b, dimension);
    }

    private int compareMinMax(byte[] b, int dimension) {
      // convert dimension to offset:
      dimension *= bytesPerDim;
      return StringHelper.compare(bytesPerDim, ranges, dimension, b, numDims * bytesPerDim + dimension);
    }

    private int compareMaxMin(byte[] b, int dimension) {
      // convert dimension to offset:
      dimension *= bytesPerDim;
      return StringHelper.compare(bytesPerDim, ranges, numDims * bytesPerDim + dimension, b, dimension);
    }

    private int compareMaxMax(byte[] b, int dimension) {
      // convert dimension to max offset:
      dimension = numDims * bytesPerDim + dimension * bytesPerDim;
      return StringHelper.compare(bytesPerDim, ranges, dimension, b, dimension);
    }
  }

  @Override
  public int hashCode() {
    int hash = classHash();
    hash = 31 * hash + field.hashCode();
    hash = 31 * hash + numDims;
    hash = 31 * hash + queryType.hashCode();
    hash = 31 * hash + Arrays.hashCode(ranges);

    return hash;
  }

  @Override
  public final boolean equals(Object o) {
    return sameClassAs(o) &&
        equalsTo(getClass().cast(o));
  }

  protected boolean equalsTo(RangeFieldQuery other) {
    return Objects.equals(field, other.field) &&
        numDims == other.numDims &&
        Arrays.equals(ranges, other.ranges) &&
        other.queryType == queryType;
  }

  @Override
  public String toString(String field) {
    StringBuilder sb = new StringBuilder();
    if (this.field.equals(field) == false) {
      sb.append(this.field);
      sb.append(':');
    }
    sb.append("<ranges:");
    sb.append(toString(ranges, 0));
    for (int d=1; d<numDims; ++d) {
      sb.append(' ');
      sb.append(toString(ranges, d));
    }
    sb.append('>');

    return sb.toString();
  }

  protected abstract String toString(byte[] ranges, int dimension);
}
