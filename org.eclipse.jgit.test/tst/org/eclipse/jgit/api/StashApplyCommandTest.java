/*
 * Copyright (C) 2012, GitHub Inc.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.StashApplyFailureException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link StashApplyCommand}
 */
public class StashApplyCommandTest extends RepositoryTestCase {

	private static final String PATH = "file.txt";

	private RevCommit head;

	private Git git;

	private File committedFile;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = Git.wrap(db);
		committedFile = writeTrashFile(PATH, "content");
		git.add().addFilepattern(PATH).call();
		head = git.commit().setMessage("add file").call();
		assertNotNull(head);
	}

	@Test
	public void workingDirectoryDelete() throws Exception {
		deleteTrashFile(PATH);
		assertFalse(committedFile.exists());
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertFalse(committedFile.exists());

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().isEmpty());
		assertTrue(status.getRemoved().isEmpty());

		assertEquals(1, status.getMissing().size());
		assertTrue(status.getMissing().contains(PATH));
	}

	@Test
	public void indexAdd() throws Exception {
		String addedPath = "file2.txt";
		File addedFile = writeTrashFile(addedPath, "content2");
		git.add().addFilepattern(addedPath).call();

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertFalse(addedFile.exists());

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertTrue(addedFile.exists());
		assertEquals("content2", read(addedFile));

		Status status = git.status().call();
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getAdded().size());
		assertTrue(status.getAdded().contains(addedPath));
	}

	@Test
	public void indexDelete() throws Exception {
		git.rm().addFilepattern("file.txt").call();

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertFalse(committedFile.exists());

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getRemoved().size());
		assertTrue(status.getRemoved().contains(PATH));
	}

	@Test
	public void workingDirectoryModify() throws Exception {
		writeTrashFile("file.txt", "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content2", read(committedFile));

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getModified().size());
		assertTrue(status.getModified().contains(PATH));
	}

	@Test
	public void workingDirectoryModifyInSubfolder() throws Exception {
		String path = "d1/d2/f.txt";
		File subfolderFile = writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		head = git.commit().setMessage("add file").call();

		writeTrashFile(path, "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(subfolderFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content2", read(subfolderFile));

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getModified().size());
		assertTrue(status.getModified().contains(path));
	}

	@Test
	public void workingDirectoryModifyIndexChanged() throws Exception {
		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		writeTrashFile("file.txt", "content3");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content3", read(committedFile));

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getChanged().size());
		assertTrue(status.getChanged().contains(PATH));
		assertEquals(1, status.getModified().size());
		assertTrue(status.getModified().contains(PATH));
	}

	@Test
	public void workingDirectoryCleanIndexModify() throws Exception {
		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		writeTrashFile("file.txt", "content");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content2", read(committedFile));

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getChanged().size());
		assertTrue(status.getChanged().contains(PATH));
	}

	@Test
	public void workingDirectoryDeleteIndexAdd() throws Exception {
		String path = "file2.txt";
		File added = writeTrashFile(path, "content2");
		assertTrue(added.exists());
		git.add().addFilepattern(path).call();
		FileUtils.delete(added);
		assertFalse(added.exists());

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertFalse(added.exists());

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content2", read(added));

		Status status = git.status().call();
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getAdded().size());
		assertTrue(status.getAdded().contains(path));
	}

	@Test
	public void workingDirectoryDeleteIndexEdit() throws Exception {
		writeTrashFile(PATH, "content2");
		git.add().addFilepattern(PATH).call();
		FileUtils.delete(committedFile);
		assertFalse(committedFile.exists());

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertFalse(committedFile.exists());

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertEquals(1, status.getChanged().size());
		assertTrue(status.getChanged().contains(PATH));
		assertTrue(status.getConflicting().isEmpty());
		assertEquals(1, status.getMissing().size());
		assertTrue(status.getMissing().contains(PATH));
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertTrue(status.getRemoved().isEmpty());
	}

	@Test
	public void multipleEdits() throws Exception {
		String addedPath = "file2.txt";
		git.rm().addFilepattern(PATH).call();
		File addedFile = writeTrashFile(addedPath, "content2");
		git.add().addFilepattern(addedPath).call();

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertTrue(committedFile.exists());
		assertFalse(addedFile.exists());

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);

		Status status = git.status().call();
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getRemoved().size());
		assertTrue(status.getRemoved().contains(PATH));
		assertEquals(1, status.getAdded().size());
		assertTrue(status.getAdded().contains(addedPath));
	}

	@Test
	public void workingDirectoryContentConflict() throws Exception {
		writeTrashFile(PATH, "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		assertTrue(git.status().call().isClean());

		writeTrashFile(PATH, "content3");

		try {
			git.stashApply().call();
			fail("Exception not thrown");
		} catch (StashApplyFailureException e) {
			// expected
 		}
		assertEquals("content3", read(PATH));
	}

	@Test
	public void stashedContentMerge() throws Exception {
		writeTrashFile(PATH, "content\nmore content\n");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("more content").call();

		writeTrashFile(PATH, "content\nhead change\nmore content\n");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("even content").call();

		writeTrashFile(PATH, "content\nstashed change\nmore content\n");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content\nhead change\nmore content\n",
				read(committedFile));
		assertTrue(git.status().call().isClean());

		writeTrashFile(PATH, "content\nmore content\ncommitted change\n");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("committed change").call();

		try {
			git.stashApply().call();
			fail("Expected conflict");
		} catch (StashApplyFailureException e) {
			// expected
		}
		Status status = new StatusCommand(db).call();
		assertEquals(1, status.getConflicting().size());
		assertEquals(
				"content\n<<<<<<< HEAD\n=======\nstashed change\n>>>>>>> stash\nmore content\ncommitted change\n",
				read(PATH));
	}

	@Test
	public void stashedApplyOnOtherBranch() throws Exception {
		writeTrashFile(PATH, "content\nmore content\n");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("more content").call();
		String path2 = "file2.txt";
		File file2 = writeTrashFile(path2, "content\nmore content\n");
		git.add().addFilepattern(PATH).call();
		git.add().addFilepattern(path2).call();
		git.commit().setMessage("even content").call();

		String otherBranch = "otherBranch";
		git.branchCreate().setName(otherBranch).call();

		writeTrashFile(PATH, "master content");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("even content").call();

		git.checkout().setName(otherBranch).call();

		writeTrashFile(PATH, "otherBranch content");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("even more content").call();

		writeTrashFile(path2, "content\nstashed change\nmore content\n");

		RevCommit stashed = git.stashCreate().call();

		assertNotNull(stashed);
		assertEquals("content\nmore content\n", read(file2));
		assertEquals("otherBranch content",
				read(committedFile));
		assertTrue(git.status().call().isClean());

		git.checkout().setName("master").call();
		git.stashApply().call();
		assertEquals("content\nstashed change\nmore content\n", read(file2));
		assertEquals("master content",
				read(committedFile));
	}

	@Test
	public void stashedApplyOnOtherBranchWithStagedChange() throws Exception {
		writeTrashFile(PATH, "content\nmore content\n");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("more content").call();
		String path2 = "file2.txt";
		File file2 = writeTrashFile(path2, "content\nmore content\n");
		git.add().addFilepattern(PATH).call();
		git.add().addFilepattern(path2).call();
		git.commit().setMessage("even content").call();

		String otherBranch = "otherBranch";
		git.branchCreate().setName(otherBranch).call();

		writeTrashFile(PATH, "master content");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("even content").call();

		git.checkout().setName(otherBranch).call();

		writeTrashFile(PATH, "otherBranch content");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("even more content").call();

		writeTrashFile(path2,
				"content\nstashed change in index\nmore content\n");
		git.add().addFilepattern(path2).call();
		writeTrashFile(path2, "content\nstashed change\nmore content\n");

		RevCommit stashed = git.stashCreate().call();

		assertNotNull(stashed);
		assertEquals("content\nmore content\n", read(file2));
		assertEquals("otherBranch content", read(committedFile));
		assertTrue(git.status().call().isClean());

		git.checkout().setName("master").call();
		git.stashApply().call();
		assertEquals("content\nstashed change\nmore content\n", read(file2));
		assertEquals(
				"[file.txt, mode:100644, content:master content]"
						+ "[file2.txt, mode:100644, content:content\nstashed change in index\nmore content\n]",
				indexState(CONTENT));
		assertEquals("master content", read(committedFile));
	}

	@Test
	public void workingDirectoryContentMerge() throws Exception {
		writeTrashFile(PATH, "content\nmore content\n");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("more content").call();

		writeTrashFile(PATH, "content\nstashed change\nmore content\n");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content\nmore content\n", read(committedFile));
		assertTrue(git.status().call().isClean());

		writeTrashFile(PATH, "content\nmore content\ncommitted change\n");
		git.add().addFilepattern(PATH).call();
		git.commit().setMessage("committed change").call();

		git.stashApply().call();
		assertEquals(
				"content\nstashed change\nmore content\ncommitted change\n",
				read(committedFile));
	}

	@Test
	public void indexContentConflict() throws Exception {
		writeTrashFile(PATH, "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		assertTrue(git.status().call().isClean());

		writeTrashFile(PATH, "content3");
		git.add().addFilepattern(PATH).call();
		writeTrashFile(PATH, "content2");

		try {
			git.stashApply().call();
			fail("Exception not thrown");
		} catch (StashApplyFailureException e) {
			// expected
		}
		assertEquals("content2", read(PATH));
	}

	@Test
	public void workingDirectoryEditPreCommit() throws Exception {
		writeTrashFile(PATH, "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		assertTrue(git.status().call().isClean());

		String path2 = "file2.txt";
		writeTrashFile(path2, "content3");
		git.add().addFilepattern(path2).call();
		assertNotNull(git.commit().setMessage("adding file").call());

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getModified().size());
		assertTrue(status.getModified().contains(PATH));
	}

	@Test
	public void stashChangeInANewSubdirectory() throws Exception {
		String subdir = "subdir";
		String fname = "file2.txt";
		String path = subdir + "/" + fname;
		String otherBranch = "otherbranch";

		writeTrashFile(subdir, fname, "content2");

		git.add().addFilepattern(path).call();
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertTrue(git.status().call().isClean());

		git.branchCreate().setName(otherBranch).call();
		git.checkout().setName(otherBranch).call();

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);

		Status status = git.status().call();
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getAdded().size());
		assertTrue(status.getAdded().contains(path));
	}

	@Test
	public void unstashNonStashCommit() throws Exception {
		try {
			git.stashApply().setStashRef(head.name()).call();
			fail("Exception not thrown");
		} catch (JGitInternalException e) {
			assertEquals(MessageFormat.format(
					JGitText.get().stashCommitIncorrectNumberOfParents,
					head.name(), "0"),
					e.getMessage());
		}
	}

	@Test
	public void unstashNoHead() throws Exception {
		Repository repo = createWorkRepository();
		try {
			Git.wrap(repo).stashApply().call();
			fail("Exception not thrown");
		} catch (NoHeadException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void noStashedCommits() throws Exception {
		try {
			git.stashApply().call();
			fail("Exception not thrown");
		} catch (InvalidRefNameException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testApplyStashWithDeletedFile() throws Exception {
		File file = writeTrashFile("file", "content");
		git.add().addFilepattern("file").call();
		git.commit().setMessage("x").call();
		file.delete();
		git.rm().addFilepattern("file").call();
		git.stashCreate().call();
		file.delete();

		git.stashApply().setStashRef("stash@{0}").call();

		assertFalse(file.exists());
	}

	@Test
	public void untrackedFileNotIncluded() throws Exception {
		String untrackedPath = "untracked.txt";
		File untrackedFile = writeTrashFile(untrackedPath, "content");
		// at least one modification needed
		writeTrashFile(PATH, "content2");
		git.add().addFilepattern(PATH).call();
		git.stashCreate().call();
		assertTrue(untrackedFile.exists());

		git.stashApply().setStashRef("stash@{0}").call();
		assertTrue(untrackedFile.exists());

		Status status = git.status().call();
		assertEquals(1, status.getUntracked().size());
		assertTrue(status.getUntracked().contains(untrackedPath));
		assertEquals(1, status.getChanged().size());
		assertTrue(status.getChanged().contains(PATH));
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getModified().isEmpty());
	}

	@Test
	public void untrackedFileIncluded() throws Exception {
		String path = "a/b/untracked.txt";
		File untrackedFile = writeTrashFile(path, "content");
		RevCommit stashedCommit = git.stashCreate().setIncludeUntracked(true)
				.call();
		assertNotNull(stashedCommit);
		assertFalse(untrackedFile.exists());
		deleteTrashFile("a/b"); // checkout should create parent dirs

		git.stashApply().setStashRef("stash@{0}").call();
		assertTrue(untrackedFile.exists());
		assertEquals("content", read(path));

		Status status = git.status().call();
		assertEquals(1, status.getUntracked().size());
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().contains(path));
	}

	@Test
	public void untrackedFileConflictsWithCommit() throws Exception {
		String path = "untracked.txt";
		writeTrashFile(path, "untracked");
		git.stashCreate().setIncludeUntracked(true).call();

		writeTrashFile(path, "committed");
		head = git.commit().setMessage("add file").call();
		git.add().addFilepattern(path).call();
		git.commit().setMessage("conflicting commit").call();

		try {
			git.stashApply().setStashRef("stash@{0}").call();
			fail("StashApplyFailureException should be thrown.");
		} catch (StashApplyFailureException e) {
			assertEquals(e.getMessage(), JGitText.get().stashApplyConflict);
		}
		assertEquals("committed", read(path));
	}

	@Test
	public void untrackedFileConflictsWithWorkingDirectory()
			throws Exception {
		String path = "untracked.txt";
		writeTrashFile(path, "untracked");
		git.stashCreate().setIncludeUntracked(true).call();

		writeTrashFile(path, "working-directory");
		try {
			git.stashApply().setStashRef("stash@{0}").call();
			fail("StashApplyFailureException should be thrown.");
		} catch (StashApplyFailureException e) {
			assertEquals(e.getMessage(), JGitText.get().stashApplyConflict);
		}
		assertEquals("working-directory", read(path));
	}
}
