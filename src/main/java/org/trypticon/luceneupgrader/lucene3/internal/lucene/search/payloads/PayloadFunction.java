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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.search.payloads;

import org.trypticon.luceneupgrader.lucene3.internal.lucene.search.Explanation;

import java.io.Serializable;


public abstract class PayloadFunction implements Serializable {

  public abstract float currentScore(int docId, String field, int start, int end, int numPayloadsSeen, float currentScore, float currentPayloadScore);

  public abstract float docScore(int docId, String field, int numPayloadsSeen, float payloadScore);
  
  public Explanation explain(int docId, int numPayloadsSeen, float payloadScore){
	  Explanation result = new Explanation();
	  result.setDescription("Unimpl Payload Function Explain");
	  result.setValue(1);
	  return result;
  };
  
  @Override
  public abstract int hashCode();
  
  @Override
  public abstract boolean equals(Object o);

}
