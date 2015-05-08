package org.trypticon.lucene3.document;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 *  Provides information about what should be done with this Field 
 *
 **/
public enum FieldSelectorResult {

    /**
     * Load this {@code Field} every time the {@code Document} is loaded, reading in the data as it is encountered.
     *  {@code Document#getField(String)} and {@code Document#getFieldable(String)} should not return null.
     *<p/>
     * {@code Document#add(Fieldable)} should be called by the Reader.
     */
  LOAD,

    /**
     * Lazily load this {@code Field}.  This means the {@code Field} is valid, but it may not actually contain its data until
     * invoked.  {@code Document#getField(String)} SHOULD NOT BE USED.  {@code Document#getFieldable(String)} is safe to use and should
     * return a valid instance of a {@code Fieldable}.
     *<p/>
     * {@code Document#add(Fieldable)} should be called by the Reader.
     */
  LAZY_LOAD,

    /**
     * Do not load the {@code Field}.  {@code Document#getField(String)} and {@code Document#getFieldable(String)} should return null.
     * {@code Document#add(Fieldable)} is not called.
     * <p/>
     * {@code Document#add(Fieldable)} should not be called by the Reader.
     */
  NO_LOAD,

    /**
     * Load this field as in the {@code #LOAD} case, but immediately return from {@code Field} loading for the {@code Document}.  Thus, the
     * Document may not have its complete set of Fields.  {@code Document#getField(String)} and {@code Document#getFieldable(String)} should
     * both be valid for this {@code Field}
     * <p/>
     * {@code Document#add(Fieldable)} should be called by the Reader.
     */
  LOAD_AND_BREAK,

    /** Expert:  Load the size of this {@code Field} rather than its value.
     * Size is measured as number of bytes required to store the field == bytes for a binary or any compressed value, and 2*chars for a String value.
     * The size is stored as a binary value, represented as an int in a byte[], with the higher order byte first in [0]
     */
  SIZE,

    /** Expert: Like {@code #SIZE} but immediately break from the field loading loop, i.e., stop loading further fields, after the size is loaded */
  SIZE_AND_BREAK,

  /**
     * Lazily load this {@code Field}, but do not cache the result.  This means the {@code Field} is valid, but it may not actually contain its data until
     * invoked.  {@code Document#getField(String)} SHOULD NOT BE USED.  {@code Document#getFieldable(String)} is safe to use and should
     * return a valid instance of a {@code Fieldable}.
     *<p/>
     * {@code Document#add(Fieldable)} should be called by the Reader.
     */
  LATENT
}