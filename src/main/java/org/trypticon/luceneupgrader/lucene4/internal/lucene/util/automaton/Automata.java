/*
 * dk.brics.automaton
 * 
 * Copyright (c) 2001-2009 Anders Moeller
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.trypticon.luceneupgrader.lucene4.internal.lucene.util.automaton;

import java.util.*;

import org.trypticon.luceneupgrader.lucene4.internal.lucene.util.BytesRef;

final public class Automata {
  
  private Automata() {}
  
  public static Automaton makeEmpty() {
    Automaton a = new Automaton();
    a.finishState();
    return a;
  }
  
  public static Automaton makeEmptyString() {
    Automaton a = new Automaton();
    a.createState();
    a.setAccept(0, true);
    return a;
  }
  
  public static Automaton makeAnyString() {
    Automaton a = new Automaton();
    int s = a.createState();
    a.setAccept(s, true);
    a.addTransition(s, s, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
    a.finishState();
    return a;
  }
  
  public static Automaton makeAnyChar() {
    return makeCharRange(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
  }

  public static int appendAnyChar(Automaton a, int state) {
    int newState = a.createState();
    a.addTransition(state, newState, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT);
    return newState;
  }

  public static Automaton makeChar(int c) {
    return makeCharRange(c, c);
  }

  public static int appendChar(Automaton a, int state, int c) {
    int newState = a.createState();
    a.addTransition(state, newState, c, c);
    return newState;
  }

  public static Automaton makeCharRange(int min, int max) {
    if (min > max) {
      return makeEmpty();
    }
    Automaton a = new Automaton();
    int s1 = a.createState();
    int s2 = a.createState();
    a.setAccept(s2, true);
    a.addTransition(s1, s2, min, max);
    a.finishState();
    return a;
  }
  
  private static int anyOfRightLength(Automaton.Builder builder, String x, int n) {
    int s = builder.createState();
    if (x.length() == n) {
      builder.setAccept(s, true);
    } else {
      builder.addTransition(s, anyOfRightLength(builder, x, n + 1), '0', '9');
    }
    return s;
  }
  
  private static int atLeast(Automaton.Builder builder, String x, int n, Collection<Integer> initials,
      boolean zeros) {
    int s = builder.createState();
    if (x.length() == n) {
      builder.setAccept(s, true);
    } else {
      if (zeros) {
        initials.add(s);
      }
      char c = x.charAt(n);
      builder.addTransition(s, atLeast(builder, x, n + 1, initials, zeros && c == '0'), c);
      if (c < '9') {
        builder.addTransition(s, anyOfRightLength(builder, x, n + 1), (char) (c + 1), '9');
      }
    }
    return s;
  }
  
  private static int atMost(Automaton.Builder builder, String x, int n) {
    int s = builder.createState();
    if (x.length() == n) {
      builder.setAccept(s, true);
    } else {
      char c = x.charAt(n);
      builder.addTransition(s, atMost(builder, x, (char) n + 1), c);
      if (c > '0') {
        builder.addTransition(s, anyOfRightLength(builder, x, n + 1), '0', (char) (c - 1));
      }
    }
    return s;
  }
  
  private static int between(Automaton.Builder builder,
      String x, String y, int n,
      Collection<Integer> initials, boolean zeros) {
    int s = builder.createState();
    if (x.length() == n) {
      builder.setAccept(s, true);
    } else {
      if (zeros) {
        initials.add(s);
      }
      char cx = x.charAt(n);
      char cy = y.charAt(n);
      if (cx == cy) {
        builder.addTransition(s, between(builder, x, y, n + 1, initials, zeros && cx == '0'), cx);
      } else { // cx<cy
        builder.addTransition(s, atLeast(builder, x, n + 1, initials, zeros && cx == '0'), cx);
        builder.addTransition(s, atMost(builder, y, n + 1), cy);
        if (cx + 1 < cy) {
          builder.addTransition(s, anyOfRightLength(builder, x, n+1), (char) (cx + 1), (char) (cy - 1));
        }
      }
    }

    return s;
  }

  public static Automaton makeInterval(int min, int max, int digits)
      throws IllegalArgumentException {
    String x = Integer.toString(min);
    String y = Integer.toString(max);
    if (min > max || (digits > 0 && y.length() > digits)) {
      throw new IllegalArgumentException();
    }
    int d;
    if (digits > 0) d = digits;
    else d = y.length();
    StringBuilder bx = new StringBuilder();
    for (int i = x.length(); i < d; i++) {
      bx.append('0');
    }
    bx.append(x);
    x = bx.toString();
    StringBuilder by = new StringBuilder();
    for (int i = y.length(); i < d; i++) {
      by.append('0');
    }
    by.append(y);
    y = by.toString();

    Automaton.Builder builder = new Automaton.Builder();

    if (digits <= 0) {
      // Reserve the "real" initial state:
      builder.createState();
    }

    Collection<Integer> initials = new ArrayList<>();

    between(builder, x, y, 0, initials, digits <= 0);

    Automaton a1 = builder.finish();

    if (digits <= 0) {
      a1.addTransition(0, 0, '0');
      for (int p : initials) {
        a1.addEpsilon(0, p);
      }
      a1.finishState();
    }

    return a1;
  }
  
  public static Automaton makeString(String s) {
    Automaton a = new Automaton();
    int lastState = a.createState();
    for (int i = 0, cp = 0; i < s.length(); i += Character.charCount(cp)) {
      int state = a.createState();
      cp = s.codePointAt(i);
      a.addTransition(lastState, state, cp, cp);
      lastState = state;
    }

    a.setAccept(lastState, true);
    a.finishState();

    assert a.isDeterministic();
    assert Operations.hasDeadStates(a) == false;

    return a;
  }
  
  public static Automaton makeString(int[] word, int offset, int length) {
    Automaton a = new Automaton();
    a.createState();
    int s = 0;
    for (int i = offset; i < offset+length; i++) {
      int s2 = a.createState();
      a.addTransition(s, s2, word[i]);
      s = s2;
    }
    a.setAccept(s, true);
    a.finishState();

    return a;
  }

  public static Automaton makeStringUnion(Collection<BytesRef> utf8Strings) {
    if (utf8Strings.isEmpty()) {
      return makeEmpty();
    } else {
      return DaciukMihovAutomatonBuilder.build(utf8Strings);
    }
  }
}
