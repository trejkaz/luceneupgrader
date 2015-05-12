package org.trypticon.luceneupgrader.lucene3.internal.lucene.index;

/**
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

import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.Directory;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.SetOnce;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * <p>Expert: a MergePolicy determines the sequence of
 * primitive merge operations.</p>
 * 
 * <p>Whenever the segments in an index have been altered by
 * {@code IndexWriter}, either the addition of a newly
 * flushed segment, addition of many segments from
 * addIndexes* calls, or a previous merge that may now need
 * to cascade, {@code IndexWriter} invokes {@code
 * #findMerges} to give the MergePolicy a chance to pick
 * merges that are now required.  This method returns a
 * {@code MergeSpecification} instance describing the set of
 * merges that should be done, or null if no merges are
 * necessary.  When IndexWriter.forceMerge is called, it calls
 * {@code #findForcedMerges(SegmentInfos,int,Map)} and the MergePolicy should
 * then return the necessary merges.</p>
 *
 * <p>Note that the policy can return more than one merge at
 * a time.  In this case, if the writer is using {@code
 * SerialMergeScheduler}, the merges will be run
 * sequentially but if it is using {@code
 * ConcurrentMergeScheduler} they will be run concurrently.</p>
 * 
 * <p>The default MergePolicy is {@code
 * TieredMergePolicy}.</p>
 *
 * @lucene.experimental
 */

public abstract class MergePolicy implements java.io.Closeable {

  /** OneMerge provides the information necessary to perform
   *  an individual primitive merge operation, resulting in
   *  a single new segment.  The merge spec includes the
   *  subset of segments to be merged as well as whether the
   *  new segment should use the compound file format. */

  public static class OneMerge {

    SegmentInfo info;               // used by IndexWriter
    boolean registerDone;           // used by IndexWriter
    long mergeGen;                  // used by IndexWriter
    boolean isExternal;             // used by IndexWriter
    int maxNumSegments = -1;        // used by IndexWriter
    public long estimatedMergeBytes;       // used by IndexWriter
    List<SegmentReader> readers;        // used by IndexWriter
    List<SegmentReader> readerClones;   // used by IndexWriter
    public final List<SegmentInfo> segments;
    public final int totalDocCount;
    boolean aborted;
    Throwable error;
    boolean paused;

    public OneMerge(List<SegmentInfo> segments) {
      if (0 == segments.size())
        throw new RuntimeException("segments must include at least one segment");
      // clone the list, as the in list may be based off original SegmentInfos and may be modified
      this.segments = new ArrayList<SegmentInfo>(segments);
      int count = 0;
      for(SegmentInfo info : segments) {
        count += info.docCount;
      }
      totalDocCount = count;
    }

    /** Record that an exception occurred while executing
     *  this merge */
    synchronized void setException(Throwable error) {
      this.error = error;
    }

    /** Retrieve previous exception set by {@code
     *  #setException}. */
    synchronized Throwable getException() {
      return error;
    }

    /** Mark this merge as aborted.  If this is called
     *  before the merge is committed then the merge will
     *  not be committed. */
    synchronized void abort() {
      aborted = true;
      notifyAll();
    }

    /** Returns true if this merge was aborted. */
    synchronized boolean isAborted() {
      return aborted;
    }

    synchronized void checkAborted(Directory dir) throws MergeAbortedException {
      if (aborted) {
        throw new MergeAbortedException("merge is aborted: " + segString(dir));
      }

      while (paused) {
        try {
          // In theory we could wait() indefinitely, but we
          // do 1000 msec, defensively
          wait(1000);
        } catch (InterruptedException ie) {
          throw new RuntimeException(ie);
        }
        if (aborted) {
          throw new MergeAbortedException("merge is aborted: " + segString(dir));
        }
      }
    }

    synchronized public void setPause(boolean paused) {
      this.paused = paused;
      if (!paused) {
        // Wakeup merge thread, if it's waiting
        notifyAll();
      }
    }

    synchronized public boolean getPause() {
      return paused;
    }

    public String segString(Directory dir) {
      StringBuilder b = new StringBuilder();
      final int numSegments = segments.size();
      for(int i=0;i<numSegments;i++) {
        if (i > 0) b.append(' ');
        b.append(segments.get(i).toString(dir, 0));
      }
      if (info != null)
        b.append(" into ").append(info.name);
      if (maxNumSegments != -1)
        b.append(" [maxNumSegments=" + maxNumSegments + "]");
      if (aborted) {
        b.append(" [ABORTED]");
      }
      return b.toString();
    }

  }

  /**
   * A MergeSpecification instance provides the information
   * necessary to perform multiple merges.  It simply
   * contains a list of {@code OneMerge} instances.
   */

  public static class MergeSpecification {

    /**
     * The subset of segments to be included in the primitive merge.
     */

    public final List<OneMerge> merges = new ArrayList<OneMerge>();

    public void add(OneMerge merge) {
      merges.add(merge);
    }

  }

  /** Exception thrown if there are any problems while
   *  executing a merge. */
  public static class MergeException extends RuntimeException {
    public MergeException(String message) {
      super(message);
    }

    public MergeException(Throwable exc) {
      super(exc);
    }
  }

  public static class MergeAbortedException extends IOException {
    public MergeAbortedException(String message) {
      super(message);
    }
  }

  protected final SetOnce<IndexWriter> writer;

  /**
   * Creates a new merge policy instance. Note that if you intend to use it
   * without passing it to {@code IndexWriter}, you should call
   * {@code #setIndexWriter(IndexWriter)}.
   */
  public MergePolicy() {
    writer = new SetOnce<IndexWriter>();
  }

  /**
   * Sets the {@code IndexWriter} to use by this merge policy. This method is
   * allowed to be called only once, and is usually set by IndexWriter. If it is
   * called more than once, {@code AlreadySetException} is thrown.
   * 
   *
   */
  public void setIndexWriter(IndexWriter writer) {
    this.writer.set(writer);
  }
  
  /**
   * Determine what set of merge operations are now necessary on the index.
   * {@code IndexWriter} calls this whenever there is a change to the segments.
   * This call is always synchronized on the {@code IndexWriter} instance so
   * only one thread at a time will call this method.
   * 
   * @param segmentInfos
   *          the total set of segments in the index
   */
  public abstract MergeSpecification findMerges(SegmentInfos segmentInfos)
      throws IOException;

  /**
   * Determine what set of merge operations is necessary in
   * order to merge to <= the specified segment count. {@code IndexWriter} calls this when its
   * {@code IndexWriter#forceMerge} method is called. This call is always
   * synchronized on the {@code IndexWriter} instance so only one thread at a
   * time will call this method.
   * 
   * @param segmentInfos
   *          the total set of segments in the index
   * @param maxSegmentCount
   *          requested maximum number of segments in the index (currently this
   *          is always 1)
   * @param segmentsToMerge
   *          contains the specific SegmentInfo instances that must be merged
   *          away. This may be a subset of all
   *          SegmentInfos.  If the value is True for a
   *          given SegmentInfo, that means this segment was
   *          an original segment present in the
   *          to-be-merged index; else, it was a segment
   *          produced by a cascaded merge.
   */
  public abstract MergeSpecification findForcedMerges(
          SegmentInfos segmentInfos, int maxSegmentCount, Map<SegmentInfo,Boolean> segmentsToMerge)
      throws IOException;

  /**
   * Release all resources for the policy.
   */
  public abstract void close();

  /** Returns true if a new segment (regardless of its origin) should use the compound file format. */
  public abstract boolean useCompoundFile(SegmentInfos segments, SegmentInfo newSegment) throws IOException;
}