/*
 * Copyright (C) 2015, Google Inc.
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
package org.eclipse.jgit.gitrepo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;

/**
 * The representation of a repo sub project.
 *
 * @see <a href="https://code.google.com/p/git-repo/">git-repo project page</a>
 * @since 4.0
 */
public class RepoProject implements Comparable<RepoProject> {
	private final String name;
	private final String path;
	private final String revision;
	private final String remote;
	private final Set<String> groups;
	private final List<CopyFile> copyfiles;
	private String url;
	private String defaultRevision;

	/**
	 * The representation of a copy file configuration.
	 */
	public static class CopyFile {
		final Repository repo;
		final String path;
		final String src;
		final String dest;

		/**
		 * @param repo
		 *            the super project.
		 * @param path
		 *            the path of the project containing this copyfile config.
		 * @param src
		 *            the source path relative to the sub repo.
		 * @param dest
		 *            the destination path relative to the super project.
		 */
		public CopyFile(Repository repo, String path, String src, String dest) {
			this.repo = repo;
			this.path = path;
			this.src = src;
			this.dest = dest;
		}

		/**
		 * Do the copy file action.
		 *
		 * @throws IOException
		 */
		public void copy() throws IOException {
			File srcFile = new File(repo.getWorkTree(),
					path + "/" + src); //$NON-NLS-1$
			File destFile = new File(repo.getWorkTree(), dest);
			FileInputStream input = new FileInputStream(srcFile);
			try {
				FileOutputStream output = new FileOutputStream(destFile);
				try {
					FileChannel channel = input.getChannel();
					output.getChannel().transferFrom(
							channel, 0, channel.size());
				} finally {
					output.close();
				}
			} finally {
				input.close();
			}
		}
	}

	/**
	 * @param name
	 *            the relative path to the {@code remote}
	 * @param path
	 *            the relative path to the super project
	 * @param revision
	 *            a SHA-1 or branch name or tag name
	 * @param remote
	 *            name of the remote definition
	 * @param groups
	 *            comma separated group list
	 */
	public RepoProject(String name, String path, String revision,
			String remote, String groups) {
		if (name == null) {
			throw new NullPointerException();
		}
		this.name = name;
		if (path != null)
			this.path = path;
		else
			this.path = name;
		this.revision = revision;
		this.remote = remote;
		this.groups = new HashSet<String>();
		if (groups != null && groups.length() > 0)
			this.groups.addAll(Arrays.asList(groups.split(","))); //$NON-NLS-1$
		copyfiles = new ArrayList<CopyFile>();
	}

	/**
	 * Set the url of the sub repo.
	 *
	 * @param url
	 * @return this for chaining.
	 */
	public RepoProject setUrl(String url) {
		this.url = url;
		return this;
	}

	/**
	 * Set the default revision for the sub repo.
	 *
	 * @param defaultRevision
	 * @return this for chaining.
	 */
	public RepoProject setDefaultRevision(String defaultRevision) {
		this.defaultRevision = defaultRevision;
		return this;
	}

	/**
	 * Get the name (relative path to the {@code remote}) of this sub repo.
	 *
	 * @return {@code name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the path (relative path to the super project) of this sub repo.
	 *
	 * @return {@code path}
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Get the revision of the sub repo.
	 *
	 * @return {@code revision} if set, or {@code defaultRevision}.
	 */
	public String getRevision() {
		return revision == null ? defaultRevision : revision;
	}

	/**
	 * Getter for the copyfile configurations.
	 *
	 * @return Immutable copy of {@code copyfiles}
	 */
	public List<CopyFile> getCopyFiles() {
		return Collections.unmodifiableList(copyfiles);
	}

	/**
	 * Get the url of the sub repo.
	 *
	 * @return {@code url}
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Get the name of the remote definition of the sub repo.
	 *
	 * @return {@code remote}
	 */
	public String getRemote() {
		return remote;
	}

	/**
	 * Test whether this sub repo belongs to a specified group.
	 *
	 * @param group
	 * @return true if {@code group} is present.
	 */
	public boolean inGroup(String group) {
		return groups.contains(group);
	}

	/**
	 * Add a copy file configuration.
	 *
	 * @param copyfile
	 */
	public void addCopyFile(CopyFile copyfile) {
		copyfiles.add(copyfile);
	}

	/**
	 * Add a bunch of copyfile configurations.
	 *
	 * @param copyfiles
	 */
	public void addCopyFiles(Collection<CopyFile> copyfiles) {
		this.copyfiles.addAll(copyfiles);
	}

	/**
	 * Clear all the copyfiles.
	 *
	 * @since 4.2
	 */
	public void clearCopyFiles() {
		this.copyfiles.clear();
	}

	private String getPathWithSlash() {
		if (path.endsWith("/")) //$NON-NLS-1$
			return path;
		else
			return path + "/"; //$NON-NLS-1$
	}

	/**
	 * Check if this sub repo is the ancestor of given sub repo.
	 *
	 * @param that
	 *            non null
	 * @return true if this sub repo is the ancestor of given sub repo.
	 */
	public boolean isAncestorOf(RepoProject that) {
		return isAncestorOf(that.getPathWithSlash());
	}

	/**
	 * Check if this sub repo is an ancestor of the given path.
	 *
	 * @param path
	 *            path to be checked to see if it is within this repository
	 * @return true if this sub repo is an ancestor of the given path.
	 * @since 4.2
	 */
	public boolean isAncestorOf(String path) {
		return path.startsWith(getPathWithSlash());
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof RepoProject) {
			RepoProject that = (RepoProject) o;
			return this.getPathWithSlash().equals(that.getPathWithSlash());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return this.getPathWithSlash().hashCode();
	}

	@Override
	public int compareTo(RepoProject that) {
		return this.getPathWithSlash().compareTo(that.getPathWithSlash());
	}
}

