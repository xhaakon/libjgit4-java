/*
 * Copyright (C) 2013, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileUtils7Test {

	private final File trash = new File(new File("target"), "trash");

	@Before
	public void setUp() throws Exception {
		FileUtils.delete(trash, FileUtils.RECURSIVE | FileUtils.RETRY | FileUtils.SKIP_MISSING);
		assertTrue(trash.mkdirs());
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.delete(trash, FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Test
	public void testDeleteSymlinkToDirectoryDoesNotDeleteTarget()
			throws IOException {
		org.junit.Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		FS fs = FS.DETECTED;
		File dir = new File(trash, "dir");
		File file = new File(dir, "file");
		File link = new File(trash, "link");
		FileUtils.mkdirs(dir);
		FileUtils.createNewFile(file);
		fs.createSymLink(link, "dir");
		FileUtils.delete(link, FileUtils.RECURSIVE);
		assertFalse(link.exists());
		assertTrue(dir.exists());
		assertTrue(file.exists());
	}

	@Test
	public void testAtomicMove() throws IOException {
		File src = new File(trash, "src");
		Files.createFile(src.toPath());
		File dst = new File(trash, "dst");
		FileUtils.rename(src, dst, StandardCopyOption.ATOMIC_MOVE);
		assertFalse(Files.exists(src.toPath()));
		assertTrue(Files.exists(dst.toPath()));
	}
}
