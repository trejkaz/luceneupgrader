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
package org.trypticon.luceneupgrader.lucene7.internal.lucene.search;


import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;

import org.trypticon.luceneupgrader.lucene7.internal.lucene.util.BytesRef;

public class SortField {

  public static enum Type {

    SCORE,

    DOC,

    STRING,

    INT,

    FLOAT,

    LONG,

    DOUBLE,

    CUSTOM,

    STRING_VAL,

    REWRITEABLE
  }

  public static final SortField FIELD_SCORE = new SortField(null, Type.SCORE);

  public static final SortField FIELD_DOC = new SortField(null, Type.DOC);

  private String field;
  private Type type;  // defaults to determining type dynamically
  boolean reverse = false;  // defaults to natural order

  // Used for CUSTOM sort
  private FieldComparatorSource comparatorSource;

  // Used for 'sortMissingFirst/Last'
  protected Object missingValue = null;

  public SortField(String field, Type type) {
    initFieldType(field, type);
  }

  public SortField(String field, Type type, boolean reverse) {
    initFieldType(field, type);
    this.reverse = reverse;
  }

  public final static Object STRING_FIRST = new Object() {
      @Override
      public String toString() {
        return "SortField.STRING_FIRST";
      }
    };
  
  public final static Object STRING_LAST = new Object() {
      @Override
      public String toString() {
        return "SortField.STRING_LAST";
      }
    };

  public Object getMissingValue() {
    return missingValue;
  }

  public void setMissingValue(Object missingValue) {
    if (type == Type.STRING || type == Type.STRING_VAL) {
      if (missingValue != STRING_FIRST && missingValue != STRING_LAST) {
        throw new IllegalArgumentException("For STRING type, missing value must be either STRING_FIRST or STRING_LAST");
      }
    } else if (type == Type.INT) {
      if (missingValue != null && missingValue.getClass() != Integer.class)
        throw new IllegalArgumentException("Missing values for Type.INT can only be of type java.lang.Integer, but got " + missingValue.getClass());
    } else if (type == Type.LONG) {
      if (missingValue != null && missingValue.getClass() != Long.class)
        throw new IllegalArgumentException("Missing values for Type.LONG can only be of type java.lang.Long, but got " + missingValue.getClass());
    } else if (type == Type.FLOAT) {
      if (missingValue != null && missingValue.getClass() != Float.class)
        throw new IllegalArgumentException("Missing values for Type.FLOAT can only be of type java.lang.Float, but got " + missingValue.getClass());
    } else if (type == Type.DOUBLE) {
      if (missingValue != null && missingValue.getClass() != Double.class)
        throw new IllegalArgumentException("Missing values for Type.DOUBLE can only be of type java.lang.Double, but got " + missingValue.getClass());
    } else {
      throw new IllegalArgumentException("Missing value only works for numeric or STRING types");
    }
    this.missingValue = missingValue;
  }

  public SortField(String field, FieldComparatorSource comparator) {
    initFieldType(field, Type.CUSTOM);
    this.comparatorSource = comparator;
  }

  public SortField(String field, FieldComparatorSource comparator, boolean reverse) {
    initFieldType(field, Type.CUSTOM);
    this.reverse = reverse;
    this.comparatorSource = comparator;
  }

  // Sets field & type, and ensures field is not NULL unless
  // type is SCORE or DOC
  private void initFieldType(String field, Type type) {
    this.type = type;
    if (field == null) {
      if (type != Type.SCORE && type != Type.DOC) {
        throw new IllegalArgumentException("field can only be null when type is SCORE or DOC");
      }
    } else {
      this.field = field;
    }
  }

  public String getField() {
    return field;
  }

  public Type getType() {
    return type;
  }

  public boolean getReverse() {
    return reverse;
  }

  public FieldComparatorSource getComparatorSource() {
    return comparatorSource;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    switch (type) {
      case SCORE:
        buffer.append("<score>");
        break;

      case DOC:
        buffer.append("<doc>");
        break;

      case STRING:
        buffer.append("<string" + ": \"").append(field).append("\">");
        break;

      case STRING_VAL:
        buffer.append("<string_val" + ": \"").append(field).append("\">");
        break;

      case INT:
        buffer.append("<int" + ": \"").append(field).append("\">");
        break;

      case LONG:
        buffer.append("<long: \"").append(field).append("\">");
        break;

      case FLOAT:
        buffer.append("<float" + ": \"").append(field).append("\">");
        break;

      case DOUBLE:
        buffer.append("<double" + ": \"").append(field).append("\">");
        break;

      case CUSTOM:
        buffer.append("<custom:\"").append(field).append("\": ").append(comparatorSource).append('>');
        break;
      
      case REWRITEABLE:
        buffer.append("<rewriteable: \"").append(field).append("\">");
        break;

      default:
        buffer.append("<???: \"").append(field).append("\">");
        break;
    }

    if (reverse) buffer.append('!');
    if (missingValue != null) {
      buffer.append(" missingValue=");
      buffer.append(missingValue);
    }

    return buffer.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SortField)) return false;
    final SortField other = (SortField)o;
    return (
      Objects.equals(other.field, this.field)
      && other.type == this.type
      && other.reverse == this.reverse
      && Objects.equals(this.comparatorSource, other.comparatorSource)
      && Objects.equals(this.missingValue, other.missingValue)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, type, reverse, comparatorSource, missingValue);
  }

  private Comparator<BytesRef> bytesComparator = Comparator.naturalOrder();

  public void setBytesComparator(Comparator<BytesRef> b) {
    bytesComparator = b;
  }

  public Comparator<BytesRef> getBytesComparator() {
    return bytesComparator;
  }

  public FieldComparator<?> getComparator(final int numHits, final int sortPos) {

    switch (type) {
    case SCORE:
      return new FieldComparator.RelevanceComparator(numHits);

    case DOC:
      return new FieldComparator.DocComparator(numHits);

    case INT:
      return new FieldComparator.IntComparator(numHits, field, (Integer) missingValue);

    case FLOAT:
      return new FieldComparator.FloatComparator(numHits, field, (Float) missingValue);

    case LONG:
      return new FieldComparator.LongComparator(numHits, field, (Long) missingValue);

    case DOUBLE:
      return new FieldComparator.DoubleComparator(numHits, field, (Double) missingValue);

    case CUSTOM:
      assert comparatorSource != null;
      return comparatorSource.newComparator(field, numHits, sortPos, reverse);

    case STRING:
      return new FieldComparator.TermOrdValComparator(numHits, field, missingValue == STRING_LAST);

    case STRING_VAL:
      return new FieldComparator.TermValComparator(numHits, field, missingValue == STRING_LAST);

    case REWRITEABLE:
      throw new IllegalStateException("SortField needs to be rewritten through Sort.rewrite(..) and SortField.rewrite(..)");
        
    default:
      throw new IllegalStateException("Illegal sort type: " + type);
    }
  }

  public SortField rewrite(IndexSearcher searcher) throws IOException {
    return this;
  }
  
  public boolean needsScores() {
    return type == Type.SCORE;
  }
}
