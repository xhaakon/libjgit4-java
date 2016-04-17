/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2007-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Tor Arne Vestbø <torarnv@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.treewalk;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;

/**
 * Working directory iterator for standard Java IO.
 * <p>
 * This iterator uses the standard <code>java.io</code> package to read the
 * specified working directory as part of a {@link TreeWalk}.
 */
public class FileTreeIterator extends WorkingTreeIterator {
	/**
	 * the starting directory of this Iterator. All entries are located directly
	 * in this directory.
	 */
	protected final File directory;

	/**
	 * the file system abstraction which will be necessary to perform certain
	 * file system operations.
	 */
	protected final FS fs;

	/**
	 * the strategy used to compute the FileMode for a FileEntry. Can be used to
	 * control things such as whether to recurse into a directory or create a
	 * gitlink.
	 *
	 * @since 4.3
	 */
	protected final FileModeStrategy fileModeStrategy;

	/**
	 * Create a new iterator to traverse the work tree and its children.
	 *
	 * @param repo
	 *            the repository whose working tree will be scanned.
	 */
	public FileTreeIterator(Repository repo) {
		this(repo,
				repo.getConfig().get(WorkingTreeOptions.KEY).isDirNoGitLinks() ?
						NoGitlinksStrategy.INSTANCE :
						DefaultFileModeStrategy.INSTANCE);
	}

	/**
	 * Create a new iterator to traverse the work tree and its children.
	 *
	 * @param repo
	 *            the repository whose working tree will be scanned.
	 * @param fileModeStrategy
	 *            the strategy to use to determine the FileMode for a FileEntry;
	 *            controls gitlinks etc.
	 *
	 * @since 4.3
	 */
	public FileTreeIterator(Repository repo, FileModeStrategy fileModeStrategy) {
		this(repo.getWorkTree(), repo.getFS(),
				repo.getConfig().get(WorkingTreeOptions.KEY),
				fileModeStrategy);
		initRootIterator(repo);
	}

	/**
	 * Create a new iterator to traverse the given directory and its children.
	 *
	 * @param root
	 *            the starting directory. This directory should correspond to
	 *            the root of the repository.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @param options
	 *            working tree options to be used
	 */
	public FileTreeIterator(final File root, FS fs, WorkingTreeOptions options) {
		this(root, fs, options, DefaultFileModeStrategy.INSTANCE);
	}

	/**
	 * Create a new iterator to traverse the given directory and its children.
	 *
	 * @param root
	 *            the starting directory. This directory should correspond to
	 *            the root of the repository.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @param options
	 *            working tree options to be used
	 * @param fileModeStrategy
	 *            the strategy to use to determine the FileMode for a FileEntry;
	 *            controls gitlinks etc.
	 *
	 * @since 4.3
	 */
	public FileTreeIterator(final File root, FS fs, WorkingTreeOptions options,
							FileModeStrategy fileModeStrategy) {
		super(options);
		directory = root;
		this.fs = fs;
		this.fileModeStrategy = fileModeStrategy;
		init(entries());
	}

	/**
	 * Create a new iterator to traverse a subdirectory.
	 *
	 * @param p
	 *            the parent iterator we were created from.
	 * @param root
	 *            the subdirectory. This should be a directory contained within
	 *            the parent directory.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @since 4.3
	 * @deprecated use {@link #FileTreeIterator(FileTreeIterator, File, FS)}
	 *             instead.
	 */
	@Deprecated
	protected FileTreeIterator(final WorkingTreeIterator p, final File root,
			FS fs) {
		this(p, root, fs, DefaultFileModeStrategy.INSTANCE);
	}

	/**
	 * Create a new iterator to traverse a subdirectory.
	 *
	 * @param p
	 *            the parent iterator we were created from.
	 * @param root
	 *            the subdirectory. This should be a directory contained within
	 *            the parent directory.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 *
	 * @since 4.3
	 */
	protected FileTreeIterator(final FileTreeIterator p, final File root,
			FS fs) {
		this(p, root, fs, p.fileModeStrategy);
	}

	/**
	 * Create a new iterator to traverse a subdirectory, given the specified
	 * FileModeStrategy.
	 *
	 * @param p
	 *            the parent iterator we were created from.
	 * @param root
	 *            the subdirectory. This should be a directory contained within
	 *            the parent directory
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @param fileModeStrategy
	 *            the strategy to use to determine the FileMode for a given
	 *            FileEntry.
	 *
	 * @since 4.3
	 */
	protected FileTreeIterator(final WorkingTreeIterator p, final File root,
			FS fs, FileModeStrategy fileModeStrategy) {
		super(p);
		directory = root;
		this.fs = fs;
		this.fileModeStrategy = fileModeStrategy;
		init(entries());
	}

	@Override
	public AbstractTreeIterator createSubtreeIterator(final ObjectReader reader)
			throws IncorrectObjectTypeException, IOException {
		return new FileTreeIterator(this, ((FileEntry) current()).getFile(), fs, fileModeStrategy);
	}

	private Entry[] entries() {
		final File[] all = directory.listFiles();
		if (all == null)
			return EOF;
		final Entry[] r = new Entry[all.length];
		for (int i = 0; i < r.length; i++)
			r[i] = new FileEntry(all[i], fs, fileModeStrategy);
		return r;
	}

	/**
	 * An interface representing the methods used to determine the FileMode for
	 * a FileEntry.
	 *
	 * @since 4.3
	 */
	public interface FileModeStrategy {
		/**
		 * Compute the FileMode for a given File, based on its attributes.
		 *
		 * @param f
		 *            the file to return a FileMode for
		 * @param attributes
		 *            the attributes of a file
		 * @return a FileMode indicating whether the file is a regular file, a
		 *         directory, a gitlink, etc.
		 */
		FileMode getMode(File f, FS.Attributes attributes);
	}

	/**
	 * A default implementation of a FileModeStrategy; defaults to treating
	 * nested .git directories as gitlinks, etc.
	 *
	 * @since 4.3
	 */
	static public class DefaultFileModeStrategy implements FileModeStrategy {
		/**
		 * a singleton instance of the default FileModeStrategy
		 */
		public final static DefaultFileModeStrategy INSTANCE =
				new DefaultFileModeStrategy();

		@Override
		public FileMode getMode(File f, FS.Attributes attributes) {
			if (attributes.isSymbolicLink()) {
				return FileMode.SYMLINK;
			} else if (attributes.isDirectory()) {
				if (new File(f, Constants.DOT_GIT).exists()) {
					return FileMode.GITLINK;
				} else {
					return FileMode.TREE;
				}
			} else if (attributes.isExecutable()) {
				return FileMode.EXECUTABLE_FILE;
			} else {
				return FileMode.REGULAR_FILE;
			}
		}
	}

	/**
	 * A FileModeStrategy that implements native git's DIR_NO_GITLINKS
	 * behavior. This is the same as the default FileModeStrategy, except
	 * all directories will be treated as directories regardless of whether
	 * or not they contain a .git directory or file.
	 *
	 * @since 4.3
	 */
	static public class NoGitlinksStrategy implements FileModeStrategy {

		/**
		 * a singleton instance of the default FileModeStrategy
		 */
		public final static NoGitlinksStrategy INSTANCE = new NoGitlinksStrategy();

		@Override
		public FileMode getMode(File f, FS.Attributes attributes) {
			if (attributes.isSymbolicLink()) {
				return FileMode.SYMLINK;
			} else if (attributes.isDirectory()) {
				return FileMode.TREE;
			} else if (attributes.isExecutable()) {
				return FileMode.EXECUTABLE_FILE;
			} else {
				return FileMode.REGULAR_FILE;
			}
		}
	}


	/**
	 * Wrapper for a standard Java IO file
	 */
	static public class FileEntry extends Entry {
		private final FileMode mode;

		private FS.Attributes attributes;

		private FS fs;

		/**
		 * Create a new file entry.
		 *
		 * @param f
		 *            file
		 * @param fs
		 *            file system
		 */
		public FileEntry(File f, FS fs) {
			this(f, fs, DefaultFileModeStrategy.INSTANCE);
		}

		/**
		 * Create a new file entry given the specified FileModeStrategy
		 *
		 * @param f
		 *            file
		 * @param fs
		 *            file system
		 * @param fileModeStrategy
		 *            the strategy to use when determining the FileMode of a
		 *            file; controls gitlinks etc.
		 *
		 * @since 4.3
		 */
		public FileEntry(File f, FS fs, FileModeStrategy fileModeStrategy) {
			this.fs = fs;
			f = fs.normalize(f);
			attributes = fs.getAttributes(f);
			mode = fileModeStrategy.getMode(f, attributes);
		}

		@Override
		public FileMode getMode() {
			return mode;
		}

		@Override
		public String getName() {
			return attributes.getName();
		}

		@Override
		public long getLength() {
			return attributes.getLength();
		}

		@Override
		public long getLastModified() {
			return attributes.getLastModifiedTime();
		}

		@Override
		public InputStream openInputStream() throws IOException {
			if (fs.isSymLink(getFile()))
				return new ByteArrayInputStream(fs.readSymLink(getFile())
						.getBytes(
						Constants.CHARACTER_ENCODING));
			else
				return new FileInputStream(getFile());
		}

		/**
		 * Get the underlying file of this entry.
		 *
		 * @return the underlying file of this entry
		 */
		public File getFile() {
			return attributes.getFile();
		}
	}

	/**
	 * @return The root directory of this iterator
	 */
	public File getDirectory() {
		return directory;
	}

	/**
	 * @return The location of the working file. This is the same as {@code new
	 *         File(getDirectory(), getEntryPath())} but may be faster by
	 *         reusing an internal File instance.
	 */
	public File getEntryFile() {
		return ((FileEntry) current()).getFile();
	}

	@Override
	protected byte[] idSubmodule(final Entry e) {
		return idSubmodule(getDirectory(), e);
	}
}
