/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.dircache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.junit.Test;

public class DirCacheLargePathTest extends RepositoryTestCase {
	@Test
	public void testPath_4090() throws Exception {
		testLongPath(4090);
	}

	@Test
	public void testPath_4094() throws Exception {
		testLongPath(4094);
	}

	@Test
	public void testPath_4095() throws Exception {
		testLongPath(4095);
	}

	@Test
	public void testPath_4096() throws Exception {
		testLongPath(4096);
	}

	@Test
	public void testPath_16384() throws Exception {
		testLongPath(16384);
	}

	private void testLongPath(final int len) throws CorruptObjectException,
			IOException {
		final String longPath = makeLongPath(len);
		final String shortPath = "~~~ shorter-path";

		final DirCacheEntry longEnt = new DirCacheEntry(longPath);
		final DirCacheEntry shortEnt = new DirCacheEntry(shortPath);

		longEnt.setFileMode(FileMode.REGULAR_FILE);
		shortEnt.setFileMode(FileMode.REGULAR_FILE);

		assertEquals(longPath, longEnt.getPathString());
		assertEquals(shortPath, shortEnt.getPathString());

		{
			final DirCache dc1 = db.lockDirCache();
			{
				final DirCacheBuilder b = dc1.builder();
				b.add(longEnt);
				b.add(shortEnt);
				assertTrue(b.commit());
			}
			assertEquals(2, dc1.getEntryCount());
			assertSame(longEnt, dc1.getEntry(0));
			assertSame(shortEnt, dc1.getEntry(1));
		}
		{
			final DirCache dc2 = db.readDirCache();
			assertEquals(2, dc2.getEntryCount());

			assertNotSame(longEnt, dc2.getEntry(0));
			assertEquals(longPath, dc2.getEntry(0).getPathString());

			assertNotSame(shortEnt, dc2.getEntry(1));
			assertEquals(shortPath, dc2.getEntry(1).getPathString());
		}
	}

	private static String makeLongPath(final int len) {
		final StringBuilder r = new StringBuilder(len);
		for (int i = 0; i < len; i++)
			r.append('a' + (i % 26));
		return r.toString();
	}
}
