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
package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

import java.io.Reader;


final class ReusableStringReader extends Reader {
  int upto;
  int left;
  String s;
  void init(String s) {
    this.s = s;
    left = s.length();
    this.upto = 0;
  }
  @Override
  public int read(char[] c) {
    return read(c, 0, c.length);
  }
  @Override
  public int read(char[] c, int off, int len) {
    if (left > len) {
      s.getChars(upto, upto+len, c, off);
      upto += len;
      left -= len;
      return len;
    } else if (0 == left) {
      // don't keep a reference (s could have been very large)
      s = null;
      return -1;
    } else {
      s.getChars(upto, upto+left, c, off);
      int r = left;
      left = 0;
      upto = s.length();
      return r;
    }
  }
  @Override
  public void close() {}
}

