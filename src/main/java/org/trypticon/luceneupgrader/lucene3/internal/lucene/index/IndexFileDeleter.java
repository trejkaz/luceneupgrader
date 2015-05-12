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
import org.trypticon.luceneupgrader.lucene3.internal.lucene.store.NoSuchDirectoryException;
import org.trypticon.luceneupgrader.lucene3.internal.lucene.util.CollectionUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/*
 * This class keeps track of each SegmentInfos instance that
 * is still "live", either because it corresponds to a
 * segments_N file in the Directory (a "commit", i.e. a
 * committed SegmentInfos) or because it's an in-memory
 * SegmentInfos that a writer is actively updating but has
 * not yet committed.  This class uses simple reference
 * counting to map the live SegmentInfos instances to
 * individual files in the Directory.
 *
 * The same directory file may be referenced by more than
 * one IndexCommit, i.e. more than one SegmentInfos.
 * Therefore we count how many commits reference each file.
 * When all the commits referencing a certain file have been
 * deleted, the refcount for that file becomes zero, and the
 * file is deleted.
 *
 * A separate deletion policy interface
 * (IndexDeletionPolicy) is consulted on creation (onInit)
 * and once per commit (onCommit), to decide when a commit
 * should be removed.
 * 
 * It is the business of the IndexDeletionPolicy to choose
 * when to delete commit points.  The actual mechanics of
 * file deletion, retrying, etc, derived from the deletion
 * of commit points is the business of the IndexFileDeleter.
 * 
 * The current default deletion policy is {@code
 * KeepOnlyLastCommitDeletionPolicy}, which removes all
 * prior commits when a new commit has completed.  This
 * matches the behavior before 2.2.
 *
 * Note that you must hold the write.lock before
 * instantiating this class.  It opens segments_N file(s)
 * directly with no retry logic.
 */

final class IndexFileDeleter {

  /* Files that we tried to delete but failed (likely
   * because they are open and we are running on Windows),
   * so we will retry them again later: */
  private List<String> deletable;

  /* Reference count for all files in the index.  
   * Counts how many existing commits reference a file.
   **/
  private Map<String, RefCount> refCounts = new HashMap<String, RefCount>();

  /* Holds all commits (segments_N) currently in the index.
   * This will have just 1 commit if you are using the
   * default delete policy (KeepOnlyLastCommitDeletionPolicy).
   * Other policies may leave commit points live for longer
   * in which case this list would be longer than 1: */
  private List<CommitPoint> commits = new ArrayList<CommitPoint>();

  /* Holds files we had incref'd from the previous
   * non-commit checkpoint: */
  private List<Collection<String>> lastFiles = new ArrayList<Collection<String>>();

  /* Commits that the IndexDeletionPolicy have decided to delete: */ 
  private List<CommitPoint> commitsToDelete = new ArrayList<CommitPoint>();

  private PrintStream infoStream;
  private Directory directory;
  private IndexDeletionPolicy policy;

  final boolean startingCommitDeleted;
  private SegmentInfos lastSegmentInfos;

  /** Change to true to see details of reference counts when
   *  infoStream != null */
  public static boolean VERBOSE_REF_COUNTS = false;

  // Used only for assert
  private final IndexWriter writer;

  void setInfoStream(PrintStream infoStream) {
    this.infoStream = infoStream;
    if (infoStream != null) {
      message("setInfoStream deletionPolicy=" + policy);
    }
  }
  
  private void message(String message) {
    infoStream.println("IFD [" + new Date() + "; " + Thread.currentThread().getName() + "]: " + message);
  }

  // called only from assert
  private boolean locked() {
    return writer == null || Thread.holdsLock(writer);
  }

  /**
   * Initialize the deleter: find all previous commits in
   * the Directory, incref the files they reference, call
   * the policy to let it delete commits.  This will remove
   * any files not referenced by any of the commits.
   * @throws CorruptIndexException if the index is corrupt
   * @throws IOException if there is a low-level IO error
   */
  public IndexFileDeleter(Directory directory, IndexDeletionPolicy policy, SegmentInfos segmentInfos, PrintStream infoStream, IndexWriter writer)
    throws IOException {

    this.infoStream = infoStream;
    this.writer = writer;

    final String currentSegmentsFile = segmentInfos.getSegmentsFileName();

    if (infoStream != null) {
      message("init: current segments file is \"" + currentSegmentsFile + "\"; deletionPolicy=" + policy);
    }

    this.policy = policy;
    this.directory = directory;

    // First pass: walk the files and initialize our ref
    // counts:
    long currentGen = segmentInfos.getGeneration();
    IndexFileNameFilter filter = IndexFileNameFilter.getFilter();

    CommitPoint currentCommitPoint = null;
    String[] files;
    try {
      files = directory.listAll();
    } catch (NoSuchDirectoryException e) {  
      // it means the directory is empty, so ignore it.
      files = new String[0];
    }

    for (String fileName : files) {

      if (filter.accept(null, fileName) && !fileName.equals(IndexFileNames.SEGMENTS_GEN)) {
        
        // Add this file to refCounts with initial count 0:
        getRefCount(fileName);

        if (fileName.startsWith(IndexFileNames.SEGMENTS)) {

          // This is a commit (segments or segments_N), and
          // it's valid (<= the max gen).  Load it, then
          // incref all files it refers to:
          if (infoStream != null) {
            message("init: load commit \"" + fileName + "\"");
          }
          SegmentInfos sis = new SegmentInfos();
          try {
            sis.read(directory, fileName);
          } catch (FileNotFoundException e) {
            // LUCENE-948: on NFS (and maybe others), if
            // you have writers switching back and forth
            // between machines, it's very likely that the
            // dir listing will be stale and will claim a
            // file segments_X exists when in fact it
            // doesn't.  So, we catch this and handle it
            // as if the file does not exist
            if (infoStream != null) {
              message("init: hit FileNotFoundException when loading commit \"" + fileName + "\"; skipping this commit point");
            }
            sis = null;
          } catch (IOException e) {
            if (SegmentInfos.generationFromSegmentsFileName(fileName) <= currentGen && directory.fileLength(fileName) > 0) {
              throw e;
            } else {
              // Most likely we are opening an index that
              // has an aborted "future" commit, so suppress
              // exc in this case
              sis = null;
            }
          }
          if (sis != null) {
            CommitPoint commitPoint = new CommitPoint(commitsToDelete, directory, sis);
            if (sis.getGeneration() == segmentInfos.getGeneration()) {
              currentCommitPoint = commitPoint;
            }
            commits.add(commitPoint);
            incRef(sis, true);

            if (lastSegmentInfos == null || sis.getGeneration() > lastSegmentInfos.getGeneration()) {
              lastSegmentInfos = sis;
            }
          }
        }
      }
    }

    if (currentCommitPoint == null && currentSegmentsFile != null) {
      // We did not in fact see the segments_N file
      // corresponding to the segmentInfos that was passed
      // in.  Yet, it must exist, because our caller holds
      // the write lock.  This can happen when the directory
      // listing was stale (eg when index accessed via NFS
      // client with stale directory listing cache).  So we
      // try now to explicitly open this commit point:
      SegmentInfos sis = new SegmentInfos();
      try {
        sis.read(directory, currentSegmentsFile);
      } catch (IOException e) {
        throw new CorruptIndexException("failed to locate current segments_N file");
      }
      if (infoStream != null) {
        message("forced open of current segments file " + segmentInfos.getSegmentsFileName());
      }
      currentCommitPoint = new CommitPoint(commitsToDelete, directory, sis);
      commits.add(currentCommitPoint);
      incRef(sis, true);
    }

    // We keep commits list in sorted order (oldest to newest):
    CollectionUtil.mergeSort(commits);

    // Now delete anything with ref count at 0.  These are
    // presumably abandoned files eg due to crash of
    // IndexWriter.
    for(Map.Entry<String, RefCount> entry : refCounts.entrySet() ) {  
      RefCount rc = entry.getValue();
      final String fileName = entry.getKey();
      if (0 == rc.count) {
        if (infoStream != null) {
          message("init: removing unreferenced file \"" + fileName + "\"");
        }
        deleteFile(fileName);
      }
    }

    // Finally, give policy a chance to remove things on
    // startup:
    if (currentSegmentsFile != null) {
      policy.onInit(commits);
    }

    // Always protect the incoming segmentInfos since
    // sometime it may not be the most recent commit
    checkpoint(segmentInfos, false);
    
    startingCommitDeleted = currentCommitPoint != null && currentCommitPoint.isDeleted();

    deleteCommits();
  }

  public SegmentInfos getLastSegmentInfos() {
    return lastSegmentInfos;
  }

  /**
   * Remove the CommitPoints in the commitsToDelete List by
   * DecRef'ing all files from each SegmentInfos.
   */
  private void deleteCommits() throws IOException {

    int size = commitsToDelete.size();

    if (size > 0) {

      // First decref all files that had been referred to by
      // the now-deleted commits:
      for(int i=0;i<size;i++) {
        CommitPoint commit = commitsToDelete.get(i);
        if (infoStream != null) {
          message("deleteCommits: now decRef commit \"" + commit.getSegmentsFileName() + "\"");
        }
        for (final String file : commit.files) {
          decRef(file);
        }
      }
      commitsToDelete.clear();

      // Now compact commits to remove deleted ones (preserving the sort):
      size = commits.size();
      int readFrom = 0;
      int writeTo = 0;
      while(readFrom < size) {
        CommitPoint commit = commits.get(readFrom);
        if (!commit.deleted) {
          if (writeTo != readFrom) {
            commits.set(writeTo, commits.get(readFrom));
          }
          writeTo++;
        }
        readFrom++;
      }

      while(size > writeTo) {
        commits.remove(size-1);
        size--;
      }
    }
  }

  /**
   * Writer calls this when it has hit an error and had to
   * roll back, to tell us that there may now be
   * unreferenced files in the filesystem.  So we re-list
   * the filesystem and delete such files.  If segmentName
   * is non-null, we will only delete files corresponding to
   * that segment.
   */
  public void refresh(String segmentName) throws IOException {
    assert locked();

    String[] files = directory.listAll();
    IndexFileNameFilter filter = IndexFileNameFilter.getFilter();
    String segmentPrefix1;
    String segmentPrefix2;
    if (segmentName != null) {
      segmentPrefix1 = segmentName + ".";
      segmentPrefix2 = segmentName + "_";
    } else {
      segmentPrefix1 = null;
      segmentPrefix2 = null;
    }

    for (String fileName : files) {
      if (filter.accept(null, fileName) &&
              (segmentName == null || fileName.startsWith(segmentPrefix1) || fileName.startsWith(segmentPrefix2)) &&
              !refCounts.containsKey(fileName) &&
              !fileName.equals(IndexFileNames.SEGMENTS_GEN)) {
        // Unreferenced file, so remove it
        if (infoStream != null) {
          message("refresh [prefix=" + segmentName + "]: removing newly created unreferenced file \"" + fileName + "\"");
        }
        deleteFile(fileName);
      }
    }
  }

  public void refresh() throws IOException {
    // Set to null so that we regenerate the list of pending
    // files; else we can accumulate same file more than
    // once
    assert locked();
    deletable = null;
    refresh(null);
  }

  public void close() throws IOException {
    // DecRef old files from the last checkpoint, if any:
    assert locked();
    int size = lastFiles.size();
    if (size > 0) {
      for (Collection<String> lastFile : lastFiles) {
        decRef(lastFile);
      }
      lastFiles.clear();
    }

    deletePendingFiles();
  }

  public void deletePendingFiles() throws IOException {
    assert locked();
    if (deletable != null) {
      List<String> oldDeletable = deletable;
      deletable = null;
      for (String anOldDeletable : oldDeletable) {
        if (infoStream != null) {
          message("delete pending file " + anOldDeletable);
        }
        deleteFile(anOldDeletable);
      }
    }
  }

  /**
   * For definition of "check point" see IndexWriter comments:
   * "Clarification: Check Points (and commits)".
   * 
   * Writer calls this when it has made a "consistent
   * change" to the index, meaning new files are written to
   * the index and the in-memory SegmentInfos have been
   * modified to point to those files.
   *
   * This may or may not be a commit (segments_N may or may
   * not have been written).
   *
   * We simply incref the files referenced by the new
   * SegmentInfos and decref the files we had previously
   * seen (if any).
   *
   * If this is a commit, we also call the policy to give it
   * a chance to remove other commits.  If any commits are
   * removed, we decref their files as well.
   */
  public void checkpoint(SegmentInfos segmentInfos, boolean isCommit) throws IOException {
    assert locked();

    if (infoStream != null) {
      message("now checkpoint \"" + segmentInfos.getSegmentsFileName() + "\" [" + segmentInfos.size() + " segments " + "; isCommit = " + isCommit + "]");
    }

    // Try again now to delete any previously un-deletable
    // files (because they were in use, on Windows):
    deletePendingFiles();

    // Incref the files:
    incRef(segmentInfos, isCommit);

    if (isCommit) {
      // Append to our commits list:
      commits.add(new CommitPoint(commitsToDelete, directory, segmentInfos));

      // Tell policy so it can remove commits:
      policy.onCommit(commits);

      // Decref files for commits that were deleted by the policy:
      deleteCommits();
    } else {
      // DecRef old files from the last checkpoint, if any:
      for (Collection<String> lastFile : lastFiles) {
        decRef(lastFile);
      }
      lastFiles.clear();

      // Save files so we can decr on next checkpoint/commit:
      lastFiles.add(segmentInfos.files(directory, false));
    }
  }

  void incRef(SegmentInfos segmentInfos, boolean isCommit) throws IOException {
    assert locked();
    // If this is a commit point, also incRef the
    // segments_N file:
    for( final String fileName: segmentInfos.files(directory, isCommit) ) {
      incRef(fileName);
    }
  }

  void incRef(Collection<String> files) throws IOException {
    assert locked();
    for(final String file : files) {
      incRef(file);
    }
  }

  void incRef(String fileName) {
    assert locked();
    RefCount rc = getRefCount(fileName);
    if (infoStream != null && VERBOSE_REF_COUNTS) {
      message("  IncRef \"" + fileName + "\": pre-incr count is " + rc.count);
    }
    rc.IncRef();
  }

  void decRef(Collection<String> files) throws IOException {
    assert locked();
    for(final String file : files) {
      decRef(file);
    }
  }

  void decRef(String fileName) throws IOException {
    assert locked();
    RefCount rc = getRefCount(fileName);
    if (infoStream != null && VERBOSE_REF_COUNTS) {
      message("  DecRef \"" + fileName + "\": pre-decr count is " + rc.count);
    }
    if (0 == rc.DecRef()) {
      // This file is no longer referenced by any past
      // commit points nor by the in-memory SegmentInfos:
      deleteFile(fileName);
      refCounts.remove(fileName);
    }
  }

  void decRef(SegmentInfos segmentInfos) throws IOException {
    assert locked();
    for (final String file : segmentInfos.files(directory, false)) {
      decRef(file);
    }
  }

  public boolean exists(String fileName) {
    assert locked();
    //noinspection SimplifiableIfStatement
    if (!refCounts.containsKey(fileName)) {
      return false;
    } else {
      return getRefCount(fileName).count > 0;
    }
  }

  private RefCount getRefCount(String fileName) {
    assert locked();
    RefCount rc;
    if (!refCounts.containsKey(fileName)) {
      rc = new RefCount(fileName);
      refCounts.put(fileName, rc);
    } else {
      rc = refCounts.get(fileName);
    }
    return rc;
  }

  /** Deletes the specified files, but only if they are new
   *  (have not yet been incref'd). */
  void deleteNewFiles(Collection<String> files) throws IOException {
    assert locked();
    for (final String fileName: files) {
      if (!refCounts.containsKey(fileName)) {
        if (infoStream != null) {
          message("delete new file \"" + fileName + "\"");
        }
        deleteFile(fileName);
      }
    }
  }

  void deleteFile(String fileName)
       throws IOException {
    assert locked();
    try {
      if (infoStream != null) {
        message("delete \"" + fileName + "\"");
      }
      directory.deleteFile(fileName);
    } catch (IOException e) {			  // if delete fails
      if (directory.fileExists(fileName)) {

        // Some operating systems (e.g. Windows) don't
        // permit a file to be deleted while it is opened
        // for read (e.g. by another process or thread). So
        // we assume that when a delete fails it is because
        // the file is open in another process, and queue
        // the file for subsequent deletion.

        if (infoStream != null) {
          message("unable to remove file \"" + fileName + "\": " + e.toString() + "; Will re-try later.");
        }
        if (deletable == null) {
          deletable = new ArrayList<String>();
        }
        deletable.add(fileName);                  // add to deletable
      }
    }
  }

  /**
   * Tracks the reference count for a single index file:
   */
  final private static class RefCount {

    // fileName used only for better assert error messages
    final String fileName;
    boolean initDone;
    RefCount(String fileName) {
      this.fileName = fileName;
    }

    int count;

    public void IncRef() {
      if (!initDone) {
        initDone = true;
      } else {
        assert count > 0: Thread.currentThread().getName() + ": RefCount is 0 pre-increment for file \"" + fileName + "\"";
      }
      ++count;
    }

    public int DecRef() {
      assert count > 0: Thread.currentThread().getName() + ": RefCount is 0 pre-decrement for file \"" + fileName + "\"";
      return --count;
    }
  }

  /**
   * Holds details for each commit point.  This class is
   * also passed to the deletion policy.  Note: this class
   * has a natural ordering that is inconsistent with
   * equals.
   */

  final private static class CommitPoint extends IndexCommit {

    Collection<String> files;
    String segmentsFileName;
    boolean deleted;
    Directory directory;
    Collection<CommitPoint> commitsToDelete;
    long version;
    long generation;

    public CommitPoint(Collection<CommitPoint> commitsToDelete, Directory directory, SegmentInfos segmentInfos) throws IOException {
      this.directory = directory;
      this.commitsToDelete = commitsToDelete;
      segmentsFileName = segmentInfos.getSegmentsFileName();
      version = segmentInfos.getVersion();
      generation = segmentInfos.getGeneration();
      files = Collections.unmodifiableCollection(segmentInfos.files(directory, true));
    }

    @Override
    public String toString() {
      return "IndexFileDeleter.CommitPoint(" + segmentsFileName + ")";
    }

    @Override
    public String getSegmentsFileName() {
      return segmentsFileName;
    }

    @Override
    public Directory getDirectory() {
      return directory;
    }

    @Override
    public long getVersion() {
      return version;
    }

    @Override
    public long getGeneration() {
      return generation;
    }

    /**
     * Called only be the deletion policy, to remove this
     * commit point from the index.
     */
    @Override
    public void delete() {
      if (!deleted) {
        deleted = true;
        commitsToDelete.add(this);
      }
    }

    @Override
    public boolean isDeleted() {
      return deleted;
    }
  }
}