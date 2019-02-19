package org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis;

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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.util.Set;


public final class TypeTokenFilter extends FilteringTokenFilter {

  private final Set<String> stopTypes;
  private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
  private final boolean useWhiteList;

  public TypeTokenFilter(boolean enablePositionIncrements, TokenStream input, Set<String> stopTypes, boolean useWhiteList) {
    super(enablePositionIncrements, input);
    this.stopTypes = stopTypes;
    this.useWhiteList = useWhiteList;
  }

  public TypeTokenFilter(boolean enablePositionIncrements, TokenStream input, Set<String> stopTypes) {
    this(enablePositionIncrements, input, stopTypes, false);
  }


  @Override
  protected boolean accept() throws IOException {
    return useWhiteList == stopTypes.contains(typeAttribute.type());
  }
}
