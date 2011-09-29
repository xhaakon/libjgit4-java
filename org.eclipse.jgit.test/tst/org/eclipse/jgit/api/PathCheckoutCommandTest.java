/*
 * Copyright (C) 2011, Kevin Sawicki <kevin@github.com>
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

import java.io.File;

import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of path-based uses of {@link CheckoutCommand}
 */
public class PathCheckoutCommandTest extends RepositoryTestCase {

	private static final String FILE1 = "f/Test.txt";

	private static final String FILE2 = "Test2.txt";

	Git git;

	RevCommit initialCommit;

	RevCommit secondCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		writeTrashFile(FILE1, "1");
		writeTrashFile(FILE2, "a");
		git.add().addFilepattern(FILE1).addFilepattern(FILE2).call();
		initialCommit = git.commit().setMessage("Initial commit").call();
		writeTrashFile(FILE1, "2");
		writeTrashFile(FILE2, "b");
		git.add().addFilepattern(FILE1).addFilepattern(FILE2).call();
		secondCommit = git.commit().setMessage("Second commit").call();
		writeTrashFile(FILE1, "3");
		writeTrashFile(FILE2, "c");
		git.add().addFilepattern(FILE1).addFilepattern(FILE2).call();
		git.commit().setMessage("Third commit").call();
	}

	@Test
	public void testUpdateWorkingDirectory() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		assertEquals("", read(written));
		co.addPath(FILE1).call();
		assertEquals("3", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	public void testCheckoutFirst() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		co.setStartPoint(initialCommit).addPath(FILE1).call();
		assertEquals("1", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	public void testCheckoutSecond() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		co.setStartPoint("HEAD~1").addPath(FILE1).call();
		assertEquals("2", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	public void testCheckoutMultiple() throws Exception {
		CheckoutCommand co = git.checkout();
		File test = writeTrashFile(FILE1, "");
		File test2 = writeTrashFile(FILE2, "");
		co.setStartPoint("HEAD~2").addPath(FILE1).addPath(FILE2).call();
		assertEquals("1", read(test));
		assertEquals("a", read(test2));
	}

	@Test
	public void testUpdateWorkingDirectoryFromIndex() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "3a");
		git.add().addFilepattern(FILE1).call();
		written = writeTrashFile(FILE1, "");
		assertEquals("", read(written));
		co.addPath(FILE1).call();
		assertEquals("3a", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	public void testUpdateWorkingDirectoryFromHeadWithIndexChange()
			throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "3a");
		git.add().addFilepattern(FILE1).call();
		written = writeTrashFile(FILE1, "");
		assertEquals("", read(written));
		co.addPath(FILE1).setStartPoint("HEAD").call();
		assertEquals("3", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

}
