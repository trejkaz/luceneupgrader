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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegExp {
  
  enum Kind {
    REGEXP_UNION, REGEXP_CONCATENATION, REGEXP_INTERSECTION, REGEXP_OPTIONAL, REGEXP_REPEAT, REGEXP_REPEAT_MIN, REGEXP_REPEAT_MINMAX, REGEXP_COMPLEMENT, REGEXP_CHAR, REGEXP_CHAR_RANGE, REGEXP_ANYCHAR, REGEXP_EMPTY, REGEXP_STRING, REGEXP_ANYSTRING, REGEXP_AUTOMATON, REGEXP_INTERVAL
  }
  
  public static final int INTERSECTION = 0x0001;
  
  public static final int COMPLEMENT = 0x0002;
  
  public static final int EMPTY = 0x0004;
  
  public static final int ANYSTRING = 0x0008;
  
  public static final int AUTOMATON = 0x0010;
  
  public static final int INTERVAL = 0x0020;
  
  public static final int ALL = 0xffff;
  
  public static final int NONE = 0x0000;

  private final String originalString;
  Kind kind;
  RegExp exp1, exp2;
  String s;
  int c;
  int min, max, digits;
  int from, to;
  
  int flags;
  int pos;
  
  RegExp() {
    this.originalString = null;
  }
  
  public RegExp(String s) throws IllegalArgumentException {
    this(s, ALL);
  }
  
  public RegExp(String s, int syntax_flags) throws IllegalArgumentException {
    originalString = s;
    flags = syntax_flags;
    RegExp e;
    if (s.length() == 0) e = makeString("");
    else {
      e = parseUnionExp();
      if (pos < originalString.length()) throw new IllegalArgumentException(
          "end-of-string expected at position " + pos);
    }
    kind = e.kind;
    exp1 = e.exp1;
    exp2 = e.exp2;
    this.s = e.s;
    c = e.c;
    min = e.min;
    max = e.max;
    digits = e.digits;
    from = e.from;
    to = e.to;
  }

  public Automaton toAutomaton() {
    return toAutomaton(null, null, Operations.DEFAULT_MAX_DETERMINIZED_STATES);
  }

  public Automaton toAutomaton(int maxDeterminizedStates)
      throws IllegalArgumentException, TooComplexToDeterminizeException {
    return toAutomaton(null, null, maxDeterminizedStates);
  }

  public Automaton toAutomaton(AutomatonProvider automaton_provider,
      int maxDeterminizedStates) throws IllegalArgumentException,
      TooComplexToDeterminizeException {
    return toAutomaton(null, automaton_provider, maxDeterminizedStates);
  }
  
  public Automaton toAutomaton(Map<String,Automaton> automata,
      int maxDeterminizedStates) throws IllegalArgumentException,
      TooComplexToDeterminizeException {
    return toAutomaton(automata, null, maxDeterminizedStates);
  }

  private Automaton toAutomaton(Map<String,Automaton> automata,
      AutomatonProvider automaton_provider, int maxDeterminizedStates)
      throws IllegalArgumentException, TooComplexToDeterminizeException {
    try {
      return toAutomatonInternal(automata, automaton_provider,
        maxDeterminizedStates);
    } catch (TooComplexToDeterminizeException e) {
      throw new TooComplexToDeterminizeException(this, e);
    }
  }

  private Automaton toAutomatonInternal(Map<String,Automaton> automata,
      AutomatonProvider automaton_provider, int maxDeterminizedStates)
      throws IllegalArgumentException {
    List<Automaton> list;
    Automaton a = null;
    switch (kind) {
      case REGEXP_UNION:
        list = new ArrayList<>();
        findLeaves(exp1, Kind.REGEXP_UNION, list, automata, automaton_provider,
          maxDeterminizedStates);
        findLeaves(exp2, Kind.REGEXP_UNION, list, automata, automaton_provider,
          maxDeterminizedStates);
        a = Operations.union(list);
        a = MinimizationOperations.minimize(a, maxDeterminizedStates);
        break;
      case REGEXP_CONCATENATION:
        list = new ArrayList<>();
        findLeaves(exp1, Kind.REGEXP_CONCATENATION, list, automata,
            automaton_provider, maxDeterminizedStates);
        findLeaves(exp2, Kind.REGEXP_CONCATENATION, list, automata,
            automaton_provider, maxDeterminizedStates);
        a = Operations.concatenate(list);
        a = MinimizationOperations.minimize(a, maxDeterminizedStates);
        break;
      case REGEXP_INTERSECTION:
        a = Operations.intersection(
            exp1.toAutomatonInternal(
              automata, automaton_provider, maxDeterminizedStates),
            exp2.toAutomatonInternal(
              automata, automaton_provider, maxDeterminizedStates));
        a = MinimizationOperations.minimize(a, maxDeterminizedStates);
        break;
      case REGEXP_OPTIONAL:
        a = Operations.optional(exp1.toAutomatonInternal(automata,
          automaton_provider, maxDeterminizedStates));
        a = MinimizationOperations.minimize(a, maxDeterminizedStates);
        break;
      case REGEXP_REPEAT:
        a = Operations.repeat(exp1.toAutomatonInternal(
          automata, automaton_provider, maxDeterminizedStates));
        a = MinimizationOperations.minimize(a, maxDeterminizedStates);
        break;
      case REGEXP_REPEAT_MIN:
        a = Operations.repeat(
          exp1.toAutomatonInternal(automata, automaton_provider,
            maxDeterminizedStates),
          min);
        a = MinimizationOperations.minimize(a, maxDeterminizedStates);
        break;
      case REGEXP_REPEAT_MINMAX:
        a = Operations.repeat(
          exp1.toAutomatonInternal(automata, automaton_provider,
            maxDeterminizedStates),
          min,
          max);
        a = MinimizationOperations.minimize(a, maxDeterminizedStates);
        break;
      case REGEXP_COMPLEMENT:
        a = Operations.complement(
          exp1.toAutomatonInternal(automata, automaton_provider,
            maxDeterminizedStates),
          maxDeterminizedStates);
        a = MinimizationOperations.minimize(a, maxDeterminizedStates);
        break;
      case REGEXP_CHAR:
        a = Automata.makeChar(c);
        break;
      case REGEXP_CHAR_RANGE:
        a = Automata.makeCharRange(from, to);
        break;
      case REGEXP_ANYCHAR:
        a = Automata.makeAnyChar();
        break;
      case REGEXP_EMPTY:
        a = Automata.makeEmpty();
        break;
      case REGEXP_STRING:
        a = Automata.makeString(s);
        break;
      case REGEXP_ANYSTRING:
        a = Automata.makeAnyString();
        break;
      case REGEXP_AUTOMATON:
        Automaton aa = null;
        if (automata != null) {
          aa = automata.get(s);
        }
        if (aa == null && automaton_provider != null) {
          try {
            aa = automaton_provider.getAutomaton(s);
          } catch (IOException e) {
            throw new IllegalArgumentException(e);
          }
        }
        if (aa == null) {
          throw new IllegalArgumentException("'" + s + "' not found");
        }
        a = aa;
        break;
      case REGEXP_INTERVAL:
        a = Automata.makeInterval(min, max, digits);
        break;
    }
    return a;
  }
  
  private void findLeaves(RegExp exp, Kind kind, List<Automaton> list,
      Map<String,Automaton> automata, AutomatonProvider automaton_provider,
      int maxDeterminizedStates) {
    if (exp.kind == kind) {
      findLeaves(exp.exp1, kind, list, automata, automaton_provider,
        maxDeterminizedStates);
      findLeaves(exp.exp2, kind, list, automata, automaton_provider,
        maxDeterminizedStates);
    } else {
      list.add(exp.toAutomatonInternal(automata, automaton_provider, 
        maxDeterminizedStates));
    }
  }

  public String getOriginalString() {
    return originalString;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    toStringBuilder(b);
    return b.toString();
  }
  
  void toStringBuilder(StringBuilder b) {
    switch (kind) {
      case REGEXP_UNION:
        b.append("(");
        exp1.toStringBuilder(b);
        b.append("|");
        exp2.toStringBuilder(b);
        b.append(")");
        break;
      case REGEXP_CONCATENATION:
        exp1.toStringBuilder(b);
        exp2.toStringBuilder(b);
        break;
      case REGEXP_INTERSECTION:
        b.append("(");
        exp1.toStringBuilder(b);
        b.append("&");
        exp2.toStringBuilder(b);
        b.append(")");
        break;
      case REGEXP_OPTIONAL:
        b.append("(");
        exp1.toStringBuilder(b);
        b.append(")?");
        break;
      case REGEXP_REPEAT:
        b.append("(");
        exp1.toStringBuilder(b);
        b.append(")*");
        break;
      case REGEXP_REPEAT_MIN:
        b.append("(");
        exp1.toStringBuilder(b);
        b.append("){").append(min).append(",}");
        break;
      case REGEXP_REPEAT_MINMAX:
        b.append("(");
        exp1.toStringBuilder(b);
        b.append("){").append(min).append(",").append(max).append("}");
        break;
      case REGEXP_COMPLEMENT:
        b.append("~(");
        exp1.toStringBuilder(b);
        b.append(")");
        break;
      case REGEXP_CHAR:
        b.append("\\").appendCodePoint(c);
        break;
      case REGEXP_CHAR_RANGE:
        b.append("[\\").appendCodePoint(from).append("-\\").appendCodePoint(to).append("]");
        break;
      case REGEXP_ANYCHAR:
        b.append(".");
        break;
      case REGEXP_EMPTY:
        b.append("#");
        break;
      case REGEXP_STRING:
        b.append("\"").append(s).append("\"");
        break;
      case REGEXP_ANYSTRING:
        b.append("@");
        break;
      case REGEXP_AUTOMATON:
        b.append("<").append(s).append(">");
        break;
      case REGEXP_INTERVAL:
        String s1 = Integer.toString(min);
        String s2 = Integer.toString(max);
        b.append("<");
        if (digits > 0) for (int i = s1.length(); i < digits; i++)
          b.append('0');
        b.append(s1).append("-");
        if (digits > 0) for (int i = s2.length(); i < digits; i++)
          b.append('0');
        b.append(s2).append(">");
        break;
    }
  }

  public String toStringTree() {
    StringBuilder b = new StringBuilder();
    toStringTree(b, "");
    return b.toString();
  }

  void toStringTree(StringBuilder b, String indent) {
    switch (kind) {
      // binary
      case REGEXP_UNION:
      case REGEXP_CONCATENATION:
      case REGEXP_INTERSECTION:
        b.append(indent);
        b.append(kind);
        b.append('\n');
        exp1.toStringTree(b, indent + "  ");
        exp2.toStringTree(b, indent + "  ");
        break;
      // unary
      case REGEXP_OPTIONAL:
      case REGEXP_REPEAT:
      case REGEXP_COMPLEMENT:
        b.append(indent);
        b.append(kind);
        b.append('\n');
        exp1.toStringTree(b, indent + "  ");
        break;
      case REGEXP_REPEAT_MIN:
        b.append(indent);
        b.append(kind);
        b.append(" min=");
        b.append(min);
        b.append('\n');
        exp1.toStringTree(b, indent + "  ");
        break;
      case REGEXP_REPEAT_MINMAX:
        b.append(indent);
        b.append(kind);
        b.append(" min=");
        b.append(min);
        b.append(" max=");
        b.append(max);
        b.append('\n');
        exp1.toStringTree(b, indent + "  ");
        break;
      case REGEXP_CHAR:
        b.append(indent);
        b.append(kind);
        b.append(" char=");
        b.appendCodePoint(c);
        b.append('\n');
        break;
      case REGEXP_CHAR_RANGE:
        b.append(indent);
        b.append(kind);
        b.append(" from=");
        b.appendCodePoint(from);
        b.append(" to=");
        b.appendCodePoint(to);
        b.append('\n');
        break;
      case REGEXP_ANYCHAR:
      case REGEXP_EMPTY:
        b.append(indent);
        b.append(kind);
        b.append('\n');
        break;
      case REGEXP_STRING:
        b.append(indent);
        b.append(kind);
        b.append(" string=");
        b.append(s);
        b.append('\n');
        break;
      case REGEXP_ANYSTRING:
        b.append(indent);
        b.append(kind);
        b.append('\n');
        break;
      case REGEXP_AUTOMATON:
        b.append(indent);
        b.append(kind);
        b.append('\n');
        break;
      case REGEXP_INTERVAL:
        b.append(indent);
        b.append(kind);
        String s1 = Integer.toString(min);
        String s2 = Integer.toString(max);
        b.append("<");
        if (digits > 0) for (int i = s1.length(); i < digits; i++)
          b.append('0');
        b.append(s1).append("-");
        if (digits > 0) for (int i = s2.length(); i < digits; i++)
          b.append('0');
        b.append(s2).append(">");
        b.append('\n');
        break;
    }
  }

  public Set<String> getIdentifiers() {
    HashSet<String> set = new HashSet<>();
    getIdentifiers(set);
    return set;
  }
  
  void getIdentifiers(Set<String> set) {
    switch (kind) {
      case REGEXP_UNION:
      case REGEXP_CONCATENATION:
      case REGEXP_INTERSECTION:
        exp1.getIdentifiers(set);
        exp2.getIdentifiers(set);
        break;
      case REGEXP_OPTIONAL:
      case REGEXP_REPEAT:
      case REGEXP_REPEAT_MIN:
      case REGEXP_REPEAT_MINMAX:
      case REGEXP_COMPLEMENT:
        exp1.getIdentifiers(set);
        break;
      case REGEXP_AUTOMATON:
        set.add(s);
        break;
      default:
    }
  }
  
  static RegExp makeUnion(RegExp exp1, RegExp exp2) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_UNION;
    r.exp1 = exp1;
    r.exp2 = exp2;
    return r;
  }
  
  static RegExp makeConcatenation(RegExp exp1, RegExp exp2) {
    if ((exp1.kind == Kind.REGEXP_CHAR || exp1.kind == Kind.REGEXP_STRING)
        && (exp2.kind == Kind.REGEXP_CHAR || exp2.kind == Kind.REGEXP_STRING)) return makeString(
        exp1, exp2);
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_CONCATENATION;
    if (exp1.kind == Kind.REGEXP_CONCATENATION
        && (exp1.exp2.kind == Kind.REGEXP_CHAR || exp1.exp2.kind == Kind.REGEXP_STRING)
        && (exp2.kind == Kind.REGEXP_CHAR || exp2.kind == Kind.REGEXP_STRING)) {
      r.exp1 = exp1.exp1;
      r.exp2 = makeString(exp1.exp2, exp2);
    } else if ((exp1.kind == Kind.REGEXP_CHAR || exp1.kind == Kind.REGEXP_STRING)
        && exp2.kind == Kind.REGEXP_CONCATENATION
        && (exp2.exp1.kind == Kind.REGEXP_CHAR || exp2.exp1.kind == Kind.REGEXP_STRING)) {
      r.exp1 = makeString(exp1, exp2.exp1);
      r.exp2 = exp2.exp2;
    } else {
      r.exp1 = exp1;
      r.exp2 = exp2;
    }
    return r;
  }
  
  static private RegExp makeString(RegExp exp1, RegExp exp2) {
    StringBuilder b = new StringBuilder();
    if (exp1.kind == Kind.REGEXP_STRING) b.append(exp1.s);
    else b.appendCodePoint(exp1.c);
    if (exp2.kind == Kind.REGEXP_STRING) b.append(exp2.s);
    else b.appendCodePoint(exp2.c);
    return makeString(b.toString());
  }
  
  static RegExp makeIntersection(RegExp exp1, RegExp exp2) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_INTERSECTION;
    r.exp1 = exp1;
    r.exp2 = exp2;
    return r;
  }
  
  static RegExp makeOptional(RegExp exp) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_OPTIONAL;
    r.exp1 = exp;
    return r;
  }
  
  static RegExp makeRepeat(RegExp exp) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_REPEAT;
    r.exp1 = exp;
    return r;
  }
  
  static RegExp makeRepeat(RegExp exp, int min) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_REPEAT_MIN;
    r.exp1 = exp;
    r.min = min;
    return r;
  }
  
  static RegExp makeRepeat(RegExp exp, int min, int max) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_REPEAT_MINMAX;
    r.exp1 = exp;
    r.min = min;
    r.max = max;
    return r;
  }
  
  static RegExp makeComplement(RegExp exp) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_COMPLEMENT;
    r.exp1 = exp;
    return r;
  }
  
  static RegExp makeChar(int c) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_CHAR;
    r.c = c;
    return r;
  }
  
  static RegExp makeCharRange(int from, int to) {
    if (from > to) 
      throw new IllegalArgumentException("invalid range: from (" + from + ") cannot be > to (" + to + ")");
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_CHAR_RANGE;
    r.from = from;
    r.to = to;
    return r;
  }
  
  static RegExp makeAnyChar() {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_ANYCHAR;
    return r;
  }
  
  static RegExp makeEmpty() {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_EMPTY;
    return r;
  }
  
  static RegExp makeString(String s) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_STRING;
    r.s = s;
    return r;
  }
  
  static RegExp makeAnyString() {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_ANYSTRING;
    return r;
  }
  
  static RegExp makeAutomaton(String s) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_AUTOMATON;
    r.s = s;
    return r;
  }
  
  static RegExp makeInterval(int min, int max, int digits) {
    RegExp r = new RegExp();
    r.kind = Kind.REGEXP_INTERVAL;
    r.min = min;
    r.max = max;
    r.digits = digits;
    return r;
  }
  
  private boolean peek(String s) {
    return more() && s.indexOf(originalString.codePointAt(pos)) != -1;
  }
  
  private boolean match(int c) {
    if (pos >= originalString.length()) return false;
    if (originalString.codePointAt(pos) == c) {
      pos += Character.charCount(c);
      return true;
    }
    return false;
  }
  
  private boolean more() {
    return pos < originalString.length();
  }
  
  private int next() throws IllegalArgumentException {
    if (!more()) throw new IllegalArgumentException("unexpected end-of-string");
    int ch = originalString.codePointAt(pos);
    pos += Character.charCount(ch);
    return ch;
  }
  
  private boolean check(int flag) {
    return (flags & flag) != 0;
  }
  
  final RegExp parseUnionExp() throws IllegalArgumentException {
    RegExp e = parseInterExp();
    if (match('|')) e = makeUnion(e, parseUnionExp());
    return e;
  }
  
  final RegExp parseInterExp() throws IllegalArgumentException {
    RegExp e = parseConcatExp();
    if (check(INTERSECTION) && match('&')) e = makeIntersection(e,
        parseInterExp());
    return e;
  }
  
  final RegExp parseConcatExp() throws IllegalArgumentException {
    RegExp e = parseRepeatExp();
    if (more() && !peek(")|") && (!check(INTERSECTION) || !peek("&"))) e = makeConcatenation(
        e, parseConcatExp());
    return e;
  }
  
  final RegExp parseRepeatExp() throws IllegalArgumentException {
    RegExp e = parseComplExp();
    while (peek("?*+{")) {
      if (match('?')) e = makeOptional(e);
      else if (match('*')) e = makeRepeat(e);
      else if (match('+')) e = makeRepeat(e, 1);
      else if (match('{')) {
        int start = pos;
        while (peek("0123456789"))
          next();
        if (start == pos) throw new IllegalArgumentException(
            "integer expected at position " + pos);
        int n = Integer.parseInt(originalString.substring(start, pos));
        int m = -1;
        if (match(',')) {
          start = pos;
          while (peek("0123456789"))
            next();
          if (start != pos) m = Integer.parseInt(
            originalString.substring(start, pos));
        } else m = n;
        if (!match('}')) throw new IllegalArgumentException(
            "expected '}' at position " + pos);
        if (m == -1) e = makeRepeat(e, n);
        else e = makeRepeat(e, n, m);
      }
    }
    return e;
  }
  
  final RegExp parseComplExp() throws IllegalArgumentException {
    if (check(COMPLEMENT) && match('~')) return makeComplement(parseComplExp());
    else return parseCharClassExp();
  }
  
  final RegExp parseCharClassExp() throws IllegalArgumentException {
    if (match('[')) {
      boolean negate = false;
      if (match('^')) negate = true;
      RegExp e = parseCharClasses();
      if (negate) e = makeIntersection(makeAnyChar(), makeComplement(e));
      if (!match(']')) throw new IllegalArgumentException(
          "expected ']' at position " + pos);
      return e;
    } else return parseSimpleExp();
  }
  
  final RegExp parseCharClasses() throws IllegalArgumentException {
    RegExp e = parseCharClass();
    while (more() && !peek("]"))
      e = makeUnion(e, parseCharClass());
    return e;
  }
  
  final RegExp parseCharClass() throws IllegalArgumentException {
    int c = parseCharExp();
    if (match('-')) return makeCharRange(c, parseCharExp());
    else return makeChar(c);
  }
  
  final RegExp parseSimpleExp() throws IllegalArgumentException {
    if (match('.')) return makeAnyChar();
    else if (check(EMPTY) && match('#')) return makeEmpty();
    else if (check(ANYSTRING) && match('@')) return makeAnyString();
    else if (match('"')) {
      int start = pos;
      while (more() && !peek("\""))
        next();
      if (!match('"')) throw new IllegalArgumentException(
          "expected '\"' at position " + pos);
      return makeString(originalString.substring(start, pos - 1));
    } else if (match('(')) {
      if (match(')')) return makeString("");
      RegExp e = parseUnionExp();
      if (!match(')')) throw new IllegalArgumentException(
          "expected ')' at position " + pos);
      return e;
    } else if ((check(AUTOMATON) || check(INTERVAL)) && match('<')) {
      int start = pos;
      while (more() && !peek(">"))
        next();
      if (!match('>')) throw new IllegalArgumentException(
          "expected '>' at position " + pos);
      String s = originalString.substring(start, pos - 1);
      int i = s.indexOf('-');
      if (i == -1) {
        if (!check(AUTOMATON)) throw new IllegalArgumentException(
            "interval syntax error at position " + (pos - 1));
        return makeAutomaton(s);
      } else {
        if (!check(INTERVAL)) throw new IllegalArgumentException(
            "illegal identifier at position " + (pos - 1));
        try {
          if (i == 0 || i == s.length() - 1 || i != s.lastIndexOf('-')) throw new NumberFormatException();
          String smin = s.substring(0, i);
          String smax = s.substring(i + 1, s.length());
          int imin = Integer.parseInt(smin);
          int imax = Integer.parseInt(smax);
          int digits;
          if (smin.length() == smax.length()) digits = smin.length();
          else digits = 0;
          if (imin > imax) {
            int t = imin;
            imin = imax;
            imax = t;
          }
          return makeInterval(imin, imax, digits);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException(
              "interval syntax error at position " + (pos - 1));
        }
      }
    } else return makeChar(parseCharExp());
  }
  
  final int parseCharExp() throws IllegalArgumentException {
    match('\\');
    return next();
  }
}
