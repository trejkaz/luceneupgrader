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
package org.trypticon.luceneupgrader.lucene4.internal.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// TODO
//   - we could try to take into account whether a large
//     merge is already running (under CMS) and then bias
//     ourselves towards picking smaller merges if so (or,
//     maybe CMS should do so)

public class TieredMergePolicy extends MergePolicy {

  public static final double DEFAULT_NO_CFS_RATIO = 0.1;
  
  private int maxMergeAtOnce = 10;
  private long maxMergedSegmentBytes = 5*1024*1024*1024L;
  private int maxMergeAtOnceExplicit = 30;

  private long floorSegmentBytes = 2*1024*1024L;
  private double segsPerTier = 10.0;
  private double forceMergeDeletesPctAllowed = 10.0;
  private double reclaimDeletesWeight = 2.0;

  public TieredMergePolicy() {
    super(DEFAULT_NO_CFS_RATIO, MergePolicy.DEFAULT_MAX_CFS_SEGMENT_SIZE);
  }


  public TieredMergePolicy setMaxMergeAtOnce(int v) {
    if (v < 2) {
      throw new IllegalArgumentException("maxMergeAtOnce must be > 1 (got " + v + ")");
    }
    maxMergeAtOnce = v;
    return this;
  }


  public int getMaxMergeAtOnce() {
    return maxMergeAtOnce;
  }

  // TODO: should addIndexes do explicit merging, too?  And,
  // if user calls IW.maybeMerge "explicitly"

  public TieredMergePolicy setMaxMergeAtOnceExplicit(int v) {
    if (v < 2) {
      throw new IllegalArgumentException("maxMergeAtOnceExplicit must be > 1 (got " + v + ")");
    }
    maxMergeAtOnceExplicit = v;
    return this;
  }


  public int getMaxMergeAtOnceExplicit() {
    return maxMergeAtOnceExplicit;
  }


  public TieredMergePolicy setMaxMergedSegmentMB(double v) {
    if (v < 0.0) {
      throw new IllegalArgumentException("maxMergedSegmentMB must be >=0 (got " + v + ")");
    }
    v *= 1024 * 1024;
    maxMergedSegmentBytes = (v > Long.MAX_VALUE) ? Long.MAX_VALUE : (long) v;
    return this;
  }


  public double getMaxMergedSegmentMB() {
    return maxMergedSegmentBytes/1024/1024.;
  }


  public TieredMergePolicy setReclaimDeletesWeight(double v) {
    if (v < 0.0) {
      throw new IllegalArgumentException("reclaimDeletesWeight must be >= 0.0 (got " + v + ")");
    }
    reclaimDeletesWeight = v;
    return this;
  }

  public double getReclaimDeletesWeight() {
    return reclaimDeletesWeight;
  }


  public TieredMergePolicy setFloorSegmentMB(double v) {
    if (v <= 0.0) {
      throw new IllegalArgumentException("floorSegmentMB must be >= 0.0 (got " + v + ")");
    }
    v *= 1024 * 1024;
    floorSegmentBytes = (v > Long.MAX_VALUE) ? Long.MAX_VALUE : (long) v;
    return this;
  }


  public double getFloorSegmentMB() {
    return floorSegmentBytes/(1024*1024.);
  }


  public TieredMergePolicy setForceMergeDeletesPctAllowed(double v) {
    if (v < 0.0 || v > 100.0) {
      throw new IllegalArgumentException("forceMergeDeletesPctAllowed must be between 0.0 and 100.0 inclusive (got " + v + ")");
    }
    forceMergeDeletesPctAllowed = v;
    return this;
  }


  public double getForceMergeDeletesPctAllowed() {
    return forceMergeDeletesPctAllowed;
  }


  public TieredMergePolicy setSegmentsPerTier(double v) {
    if (v < 2.0) {
      throw new IllegalArgumentException("segmentsPerTier must be >= 2.0 (got " + v + ")");
    }
    segsPerTier = v;
    return this;
  }


  public double getSegmentsPerTier() {
    return segsPerTier;
  }

  private class SegmentByteSizeDescending implements Comparator<SegmentCommitInfo> {

    private final IndexWriter writer;

    SegmentByteSizeDescending(IndexWriter writer) {
      this.writer = writer;
    }
    @Override
    public int compare(SegmentCommitInfo o1, SegmentCommitInfo o2) {
      try {
        final long sz1 = size(o1, writer);
        final long sz2 = size(o2, writer);
        if (sz1 > sz2) {
          return -1;
        } else if (sz2 > sz1) {
          return 1;
        } else {
          return o1.info.name.compareTo(o2.info.name);
        }
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
  }

  protected static abstract class MergeScore {
    protected MergeScore() {
    }
    
    abstract double getScore();

    abstract String getExplanation();
  }

  @Override
  public MergeSpecification findMerges(MergeTrigger mergeTrigger, SegmentInfos infos, IndexWriter writer) throws IOException {
    if (verbose(writer)) {
      message("findMerges: " + infos.size() + " segments", writer);
    }
    if (infos.size() == 0) {
      return null;
    }
    final Collection<SegmentCommitInfo> merging = writer.getMergingSegments();
    final Collection<SegmentCommitInfo> toBeMerged = new HashSet<>();

    final List<SegmentCommitInfo> infosSorted = new ArrayList<>(infos.asList());
    Collections.sort(infosSorted, new SegmentByteSizeDescending(writer));

    // Compute total index bytes & print details about the index
    long totIndexBytes = 0;
    long minSegmentBytes = Long.MAX_VALUE;
    for(SegmentCommitInfo info : infosSorted) {
      final long segBytes = size(info, writer);
      if (verbose(writer)) {
        String extra = merging.contains(info) ? " [merging]" : "";
        if (segBytes >= maxMergedSegmentBytes/2.0) {
          extra += " [skip: too large]";
        } else if (segBytes < floorSegmentBytes) {
          extra += " [floored]";
        }
        message("  seg=" + writer.segString(info) + " size=" + String.format(Locale.ROOT, "%.3f", segBytes/1024/1024.) + " MB" + extra, writer);
      }

      minSegmentBytes = Math.min(segBytes, minSegmentBytes);
      // Accum total byte size
      totIndexBytes += segBytes;
    }

    // If we have too-large segments, grace them out
    // of the maxSegmentCount:
    int tooBigCount = 0;
    while (tooBigCount < infosSorted.size() && size(infosSorted.get(tooBigCount), writer) >= maxMergedSegmentBytes/2.0) {
      totIndexBytes -= size(infosSorted.get(tooBigCount), writer);
      tooBigCount++;
    }

    minSegmentBytes = floorSize(minSegmentBytes);

    // Compute max allowed segs in the index
    long levelSize = minSegmentBytes;
    long bytesLeft = totIndexBytes;
    double allowedSegCount = 0;
    while(true) {
      final double segCountLevel = bytesLeft / (double) levelSize;
      if (segCountLevel < segsPerTier) {
        allowedSegCount += Math.ceil(segCountLevel);
        break;
      }
      allowedSegCount += segsPerTier;
      bytesLeft -= segsPerTier * levelSize;
      levelSize *= maxMergeAtOnce;
    }
    int allowedSegCountInt = (int) allowedSegCount;

    MergeSpecification spec = null;

    // Cycle to possibly select more than one merge:
    while(true) {

      long mergingBytes = 0;

      // Gather eligible segments for merging, ie segments
      // not already being merged and not already picked (by
      // prior iteration of this loop) for merging:
      final List<SegmentCommitInfo> eligible = new ArrayList<>();
      for(int idx = tooBigCount; idx<infosSorted.size(); idx++) {
        final SegmentCommitInfo info = infosSorted.get(idx);
        if (merging.contains(info)) {
          mergingBytes += info.sizeInBytes();
        } else if (!toBeMerged.contains(info)) {
          eligible.add(info);
        }
      }

      final boolean maxMergeIsRunning = mergingBytes >= maxMergedSegmentBytes;

      if (verbose(writer)) {
        message("  allowedSegmentCount=" + allowedSegCountInt + " vs count=" + infosSorted.size() + " (eligible count=" + eligible.size() + ") tooBigCount=" + tooBigCount, writer);
      }

      if (eligible.size() == 0) {
        return spec;
      }

      if (eligible.size() > allowedSegCountInt) {

        // OK we are over budget -- find best merge!
        MergeScore bestScore = null;
        List<SegmentCommitInfo> best = null;
        boolean bestTooLarge = false;
        long bestMergeBytes = 0;

        // Consider all merge starts:
        for(int startIdx = 0;startIdx <= eligible.size()-maxMergeAtOnce; startIdx++) {

          long totAfterMergeBytes = 0;

          final List<SegmentCommitInfo> candidate = new ArrayList<>();
          boolean hitTooLarge = false;
          for(int idx = startIdx;idx<eligible.size() && candidate.size() < maxMergeAtOnce;idx++) {
            final SegmentCommitInfo info = eligible.get(idx);
            final long segBytes = size(info, writer);

            if (totAfterMergeBytes + segBytes > maxMergedSegmentBytes) {
              hitTooLarge = true;
              // NOTE: we continue, so that we can try
              // "packing" smaller segments into this merge
              // to see if we can get closer to the max
              // size; this in general is not perfect since
              // this is really "bin packing" and we'd have
              // to try different permutations.
              continue;
            }
            candidate.add(info);
            totAfterMergeBytes += segBytes;
          }

          final MergeScore score = score(candidate, hitTooLarge, mergingBytes, writer);
          if (verbose(writer)) {
            message("  maybe=" + writer.segString(candidate) + " score=" + score.getScore() + " " + score.getExplanation() + " tooLarge=" + hitTooLarge + " size=" + String.format(Locale.ROOT, "%.3f MB", totAfterMergeBytes/1024./1024.), writer);
          }

          // If we are already running a max sized merge
          // (maxMergeIsRunning), don't allow another max
          // sized merge to kick off:
          if ((bestScore == null || score.getScore() < bestScore.getScore()) && (!hitTooLarge || !maxMergeIsRunning)) {
            best = candidate;
            bestScore = score;
            bestTooLarge = hitTooLarge;
            bestMergeBytes = totAfterMergeBytes;
          }
        }
        
        if (best != null) {
          if (spec == null) {
            spec = new MergeSpecification();
          }
          final OneMerge merge = new OneMerge(best);
          spec.add(merge);
          for(SegmentCommitInfo info : merge.segments) {
            toBeMerged.add(info);
          }

          if (verbose(writer)) {
            message("  add merge=" + writer.segString(merge.segments) + " size=" + String.format(Locale.ROOT, "%.3f MB", bestMergeBytes/1024./1024.) + " score=" + String.format(Locale.ROOT, "%.3f", bestScore.getScore()) + " " + bestScore.getExplanation() + (bestTooLarge ? " [max merge]" : ""), writer);
          }
        } else {
          return spec;
        }
      } else {
        return spec;
      }
    }
  }

  protected MergeScore score(List<SegmentCommitInfo> candidate, boolean hitTooLarge, long mergingBytes, IndexWriter writer) throws IOException {
    long totBeforeMergeBytes = 0;
    long totAfterMergeBytes = 0;
    long totAfterMergeBytesFloored = 0;
    for(SegmentCommitInfo info : candidate) {
      final long segBytes = size(info, writer);
      totAfterMergeBytes += segBytes;
      totAfterMergeBytesFloored += floorSize(segBytes);
      totBeforeMergeBytes += info.sizeInBytes();
    }

    // Roughly measure "skew" of the merge, i.e. how
    // "balanced" the merge is (whether the segments are
    // about the same size), which can range from
    // 1.0/numSegsBeingMerged (good) to 1.0 (poor). Heavily
    // lopsided merges (skew near 1.0) is no good; it means
    // O(N^2) merge cost over time:
    final double skew;
    if (hitTooLarge) {
      // Pretend the merge has perfect skew; skew doesn't
      // matter in this case because this merge will not
      // "cascade" and so it cannot lead to N^2 merge cost
      // over time:
      skew = 1.0/maxMergeAtOnce;
    } else {
      skew = ((double) floorSize(size(candidate.get(0), writer)))/totAfterMergeBytesFloored;
    }

    // Strongly favor merges with less skew (smaller
    // mergeScore is better):
    double mergeScore = skew;

    // Gently favor smaller merges over bigger ones.  We
    // don't want to make this exponent too large else we
    // can end up doing poor merges of small segments in
    // order to avoid the large merges:
    mergeScore *= Math.pow(totAfterMergeBytes, 0.05);

    // Strongly favor merges that reclaim deletes:
    final double nonDelRatio = ((double) totAfterMergeBytes)/totBeforeMergeBytes;
    mergeScore *= Math.pow(nonDelRatio, reclaimDeletesWeight);

    final double finalMergeScore = mergeScore;

    return new MergeScore() {

      @Override
      public double getScore() {
        return finalMergeScore;
      }

      @Override
      public String getExplanation() {
        return "skew=" + String.format(Locale.ROOT, "%.3f", skew) + " nonDelRatio=" + String.format(Locale.ROOT, "%.3f", nonDelRatio);
      }
    };
  }

  @Override
  public MergeSpecification findForcedMerges(SegmentInfos infos, int maxSegmentCount, Map<SegmentCommitInfo,Boolean> segmentsToMerge, IndexWriter writer) throws IOException {
    if (verbose(writer)) {
      message("findForcedMerges maxSegmentCount=" + maxSegmentCount + " infos=" + writer.segString(infos) + " segmentsToMerge=" + segmentsToMerge, writer);
    }

    List<SegmentCommitInfo> eligible = new ArrayList<>();
    boolean forceMergeRunning = false;
    final Collection<SegmentCommitInfo> merging = writer.getMergingSegments();
    boolean segmentIsOriginal = false;
    for(SegmentCommitInfo info : infos) {
      final Boolean isOriginal = segmentsToMerge.get(info);
      if (isOriginal != null) {
        segmentIsOriginal = isOriginal;
        if (!merging.contains(info)) {
          eligible.add(info);
        } else {
          forceMergeRunning = true;
        }
      }
    }

    if (eligible.size() == 0) {
      return null;
    }

    if ((maxSegmentCount > 1 && eligible.size() <= maxSegmentCount) ||
        (maxSegmentCount == 1 && eligible.size() == 1 && (!segmentIsOriginal || isMerged(infos, eligible.get(0), writer)))) {
      if (verbose(writer)) {
        message("already merged", writer);
      }
      return null;
    }

    Collections.sort(eligible, new SegmentByteSizeDescending(writer));

    if (verbose(writer)) {
      message("eligible=" + eligible, writer);
      message("forceMergeRunning=" + forceMergeRunning, writer);
    }

    int end = eligible.size();
    
    MergeSpecification spec = null;

    // Do full merges, first, backwards:
    while(end >= maxMergeAtOnceExplicit + maxSegmentCount - 1) {
      if (spec == null) {
        spec = new MergeSpecification();
      }
      final OneMerge merge = new OneMerge(eligible.subList(end-maxMergeAtOnceExplicit, end));
      if (verbose(writer)) {
        message("add merge=" + writer.segString(merge.segments), writer);
      }
      spec.add(merge);
      end -= maxMergeAtOnceExplicit;
    }

    if (spec == null && !forceMergeRunning) {
      // Do final merge
      final int numToMerge = end - maxSegmentCount + 1;
      final OneMerge merge = new OneMerge(eligible.subList(end-numToMerge, end));
      if (verbose(writer)) {
        message("add final merge=" + merge.segString(writer.getDirectory()), writer);
      }
      spec = new MergeSpecification();
      spec.add(merge);
    }

    return spec;
  }

  @Override
  public MergeSpecification findForcedDeletesMerges(SegmentInfos infos, IndexWriter writer) throws IOException {
    if (verbose(writer)) {
      message("findForcedDeletesMerges infos=" + writer.segString(infos) + " forceMergeDeletesPctAllowed=" + forceMergeDeletesPctAllowed, writer);
    }
    final List<SegmentCommitInfo> eligible = new ArrayList<>();
    final Collection<SegmentCommitInfo> merging = writer.getMergingSegments();
    for(SegmentCommitInfo info : infos) {
      double pctDeletes = 100.*((double) writer.numDeletedDocs(info))/info.info.getDocCount();
      if (pctDeletes > forceMergeDeletesPctAllowed && !merging.contains(info)) {
        eligible.add(info);
      }
    }

    if (eligible.size() == 0) {
      return null;
    }

    Collections.sort(eligible, new SegmentByteSizeDescending(writer));

    if (verbose(writer)) {
      message("eligible=" + eligible, writer);
    }

    int start = 0;
    MergeSpecification spec = null;

    while(start < eligible.size()) {
      // Don't enforce max merged size here: app is explicitly
      // calling forceMergeDeletes, and knows this may take a
      // long time / produce big segments (like forceMerge):
      final int end = Math.min(start + maxMergeAtOnceExplicit, eligible.size());
      if (spec == null) {
        spec = new MergeSpecification();
      }

      final OneMerge merge = new OneMerge(eligible.subList(start, end));
      if (verbose(writer)) {
        message("add merge=" + writer.segString(merge.segments), writer);
      }
      spec.add(merge);
      start = end;
    }

    return spec;
  }

  private long floorSize(long bytes) {
    return Math.max(floorSegmentBytes, bytes);
  }

  private boolean verbose(IndexWriter writer) {
    return writer != null && writer.infoStream.isEnabled("TMP");
  }

  private void message(String message, IndexWriter writer) {
    writer.infoStream.message("TMP", message);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[" + getClass().getSimpleName() + ": ");
    sb.append("maxMergeAtOnce=").append(maxMergeAtOnce).append(", ");
    sb.append("maxMergeAtOnceExplicit=").append(maxMergeAtOnceExplicit).append(", ");
    sb.append("maxMergedSegmentMB=").append(maxMergedSegmentBytes/1024/1024.).append(", ");
    sb.append("floorSegmentMB=").append(floorSegmentBytes/1024/1024.).append(", ");
    sb.append("forceMergeDeletesPctAllowed=").append(forceMergeDeletesPctAllowed).append(", ");
    sb.append("segmentsPerTier=").append(segsPerTier).append(", ");
    sb.append("maxCFSSegmentSizeMB=").append(getMaxCFSSegmentSizeMB()).append(", ");
    sb.append("noCFSRatio=").append(noCFSRatio);
    return sb.toString();
  }
}
