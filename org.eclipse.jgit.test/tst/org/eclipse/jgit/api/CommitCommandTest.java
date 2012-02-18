/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

/**
 * Unit tests of {@link CommitCommand}
 */
public class CommitCommandTest extends RepositoryTestCase {

	@Test
	public void testExecutableRetention() throws Exception {
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_FILEMODE, true);
		config.save();

		FS executableFs = new FS() {

			public boolean supportsExecute() {
				return true;
			}

			public boolean setExecute(File f, boolean canExec) {
				return true;
			}

			public ProcessBuilder runInShell(String cmd, String[] args) {
				return null;
			}

			public boolean retryFailedLockFileCommit() {
				return false;
			}

			public FS newInstance() {
				return this;
			}

			protected File discoverGitPrefix() {
				return null;
			}

			public boolean canExecute(File f) {
				return true;
			}
		};

		Git git = Git.open(db.getDirectory(), executableFs);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		RevCommit commit1 = git.commit().setMessage("commit").call();
		TreeWalk walk = TreeWalk.forPath(db, path, commit1.getTree());
		assertNotNull(walk);
		assertEquals(FileMode.EXECUTABLE_FILE, walk.getFileMode(0));

		FS nonExecutableFs = new FS() {

			public boolean supportsExecute() {
				return false;
			}

			public boolean setExecute(File f, boolean canExec) {
				return false;
			}

			public ProcessBuilder runInShell(String cmd, String[] args) {
				return null;
			}

			public boolean retryFailedLockFileCommit() {
				return false;
			}

			public FS newInstance() {
				return this;
			}

			protected File discoverGitPrefix() {
				return null;
			}

			public boolean canExecute(File f) {
				return false;
			}
		};

		config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_FILEMODE, false);
		config.save();

		Git git2 = Git.open(db.getDirectory(), nonExecutableFs);
		writeTrashFile(path, "content2");
		RevCommit commit2 = git2.commit().setOnly(path).setMessage("commit2")
				.call();
		walk = TreeWalk.forPath(db, path, commit2.getTree());
		assertNotNull(walk);
		assertEquals(FileMode.EXECUTABLE_FILE, walk.getFileMode(0));
	}

	@Test
	public void commitNewSubmodule() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		SubmoduleAddCommand command = new SubmoduleAddCommand(db);
		String path = "sub";
		command.setPath(path);
		String uri = db.getDirectory().toURI().toString();
		command.setURI(uri);
		Repository repo = command.call();
		assertNotNull(repo);

		SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
		assertEquals(path, generator.getPath());
		assertEquals(commit, generator.getObjectId());
		assertEquals(uri, generator.getModulesUrl());
		assertEquals(path, generator.getModulesPath());
		assertEquals(uri, generator.getConfigUrl());
		assertNotNull(generator.getRepository());
		assertEquals(commit, repo.resolve(Constants.HEAD));

		RevCommit submoduleCommit = git.commit().setMessage("submodule add")
				.setOnly(path).call();
		assertNotNull(submoduleCommit);
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(commit.getTree());
		walk.addTree(submoduleCommit.getTree());
		walk.setFilter(TreeFilter.ANY_DIFF);
		List<DiffEntry> diffs = DiffEntry.scan(walk);
		assertEquals(1, diffs.size());
		DiffEntry subDiff = diffs.get(0);
		assertEquals(FileMode.MISSING, subDiff.getOldMode());
		assertEquals(FileMode.GITLINK, subDiff.getNewMode());
		assertEquals(ObjectId.zeroId(), subDiff.getOldId().toObjectId());
		assertEquals(commit, subDiff.getNewId().toObjectId());
		assertEquals(path, subDiff.getNewPath());
	}

	@Test
	public void commitSubmoduleUpdate() throws Exception {
		Git git = new Git(db);
		writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();
		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("edit file").call();

		SubmoduleAddCommand command = new SubmoduleAddCommand(db);
		String path = "sub";
		command.setPath(path);
		String uri = db.getDirectory().toURI().toString();
		command.setURI(uri);
		Repository repo = command.call();
		assertNotNull(repo);

		SubmoduleWalk generator = SubmoduleWalk.forIndex(db);
		assertTrue(generator.next());
		assertEquals(path, generator.getPath());
		assertEquals(commit2, generator.getObjectId());
		assertEquals(uri, generator.getModulesUrl());
		assertEquals(path, generator.getModulesPath());
		assertEquals(uri, generator.getConfigUrl());
		assertNotNull(generator.getRepository());
		assertEquals(commit2, repo.resolve(Constants.HEAD));

		RevCommit submoduleAddCommit = git.commit().setMessage("submodule add")
				.setOnly(path).call();
		assertNotNull(submoduleAddCommit);

		RefUpdate update = repo.updateRef(Constants.HEAD);
		update.setNewObjectId(commit);
		assertEquals(Result.FORCED, update.forceUpdate());

		RevCommit submoduleEditCommit = git.commit()
				.setMessage("submodule add").setOnly(path).call();
		assertNotNull(submoduleEditCommit);
		TreeWalk walk = new TreeWalk(db);
		walk.addTree(submoduleAddCommit.getTree());
		walk.addTree(submoduleEditCommit.getTree());
		walk.setFilter(TreeFilter.ANY_DIFF);
		List<DiffEntry> diffs = DiffEntry.scan(walk);
		assertEquals(1, diffs.size());
		DiffEntry subDiff = diffs.get(0);
		assertEquals(FileMode.GITLINK, subDiff.getOldMode());
		assertEquals(FileMode.GITLINK, subDiff.getNewMode());
		assertEquals(commit2, subDiff.getOldId().toObjectId());
		assertEquals(commit, subDiff.getNewId().toObjectId());
		assertEquals(path, subDiff.getNewPath());
		assertEquals(path, subDiff.getOldPath());
	}
}
