/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class CheckoutCommandTest extends RepositoryTestCase {
	private Git git;

	RevCommit initialCommit;

	RevCommit secondCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		initialCommit = git.commit().setMessage("Initial commit").call();

		// create a master branch and switch to it
		git.branchCreate().setName("test").call();
		RefUpdate rup = db.updateRef(Constants.HEAD);
		rup.link("refs/heads/test");

		// commit something on the test branch
		writeTrashFile("Test.txt", "Some change");
		git.add().addFilepattern("Test.txt").call();
		secondCommit = git.commit().setMessage("Second commit").call();
	}

	@Test
	public void testSimpleCheckout() {
		try {
			git.checkout().setName("test").call();
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testCheckout() {
		try {
			git.checkout().setName("test").call();
			assertEquals("[Test.txt, mode:100644, content:Some change]",
					indexState(CONTENT));
			Ref result = git.checkout().setName("master").call();
			assertEquals("[Test.txt, mode:100644, content:Hello world]",
					indexState(CONTENT));
			assertEquals("refs/heads/master", result.getName());
			assertEquals("refs/heads/master", git.getRepository()
					.getFullBranch());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testCreateBranchOnCheckout() throws IOException {
		try {
			git.checkout().setCreateBranch(true).setName("test2").call();
		} catch (Exception e) {
			fail(e.getMessage());
		}
		assertNotNull(db.getRef("test2"));
	}

	@Test
	public void testCheckoutToNonExistingBranch() throws JGitInternalException,
			RefAlreadyExistsException, InvalidRefNameException {
		try {
			git.checkout().setName("badbranch").call();
			fail("Should have failed");
		} catch (RefNotFoundException e) {
			// except to hit here
		}
	}

	@Test
	public void testCheckoutWithConflict() {
		CheckoutCommand co = git.checkout();
		try {
			writeTrashFile("Test.txt", "Another change");
			assertEquals(Status.NOT_TRIED, co.getResult().getStatus());
			co.setName("master").call();
			fail("Should have failed");
		} catch (Exception e) {
			assertEquals(Status.CONFLICTS, co.getResult().getStatus());
			assertTrue(co.getResult().getConflictList().contains("Test.txt"));
		}
	}

	@Test
	public void testCheckoutWithNonDeletedFiles() throws Exception {
		File testFile = writeTrashFile("temp", "");
		FileInputStream fis = new FileInputStream(testFile);
		try {
			FileUtils.delete(testFile);
			return;
		} catch (IOException e) {
			// the test makes only sense if deletion of
			// a file with open stream fails
		}
		fis.close();
		FileUtils.delete(testFile);
		CheckoutCommand co = git.checkout();
		// delete Test.txt in branch test
		testFile = new File(db.getWorkTree(), "Test.txt");
		assertTrue(testFile.exists());
		FileUtils.delete(testFile);
		assertFalse(testFile.exists());
		git.add().addFilepattern("Test.txt");
		git.commit().setMessage("Delete Test.txt").setAll(true).call();
		git.checkout().setName("master").call();
		assertTrue(testFile.exists());
		// lock the file so it can't be deleted (in Windows, that is)
		fis = new FileInputStream(testFile);
		try {
			assertEquals(Status.NOT_TRIED, co.getResult().getStatus());
			co.setName("test").call();
			assertTrue(testFile.exists());
			assertEquals(Status.NONDELETED, co.getResult().getStatus());
			assertTrue(co.getResult().getUndeletedList().contains("Test.txt"));
		} finally {
			fis.close();
		}
	}

	@Test
	public void testCheckoutCommit() {
		try {
			Ref result = git.checkout().setName(initialCommit.name()).call();
			assertEquals("[Test.txt, mode:100644, content:Hello world]",
					indexState(CONTENT));
			assertNull(result);
			assertEquals(initialCommit.name(), git.getRepository()
					.getFullBranch());
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testCheckoutRemoteTrackingWithoutLocalBranch() {
		try {
			// create second repository
			Repository db2 = createWorkRepository();
			Git git2 = new Git(db2);

			// setup the second repository to fetch from the first repository
			final StoredConfig config = db2.getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
			URIish uri = new URIish(db.getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.update(config);
			config.save();

			// fetch from first repository
			RefSpec spec = new RefSpec("+refs/heads/*:refs/remotes/origin/*");
			git2.fetch().setRemote("origin").setRefSpecs(spec).call();
			// checkout remote tracking branch in second repository
			// (no local branches exist yet in second repository)
			git2.checkout().setName("remotes/origin/test").call();
			assertEquals("[Test.txt, mode:100644, content:Some change]",
					indexState(db2, CONTENT));
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testDetachedHeadOnCheckout() throws JGitInternalException,
			RefAlreadyExistsException, RefNotFoundException,
			InvalidRefNameException, IOException {
		CheckoutCommand co = git.checkout();
		co.setName("master").call();

		String commitId = db.getRef(Constants.MASTER).getObjectId().name();
		co = git.checkout();
		co.setName(commitId).call();

		Ref head = db.getRef(Constants.HEAD);
		assertFalse(head.isSymbolic());
		assertSame(head, head.getTarget());
	}
}
