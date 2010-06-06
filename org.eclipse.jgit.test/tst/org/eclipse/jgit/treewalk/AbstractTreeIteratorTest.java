/*
 * Copyright (C) 2009, Google Inc.
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

import java.io.IOException;

import junit.framework.TestCase;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;


public class AbstractTreeIteratorTest extends TestCase {
	private static String prefix(String path) {
		final int s = path.lastIndexOf('/');
		return s > 0 ? path.substring(0, s) : "";
	}

	public class FakeTreeIterator extends WorkingTreeIterator {
		public FakeTreeIterator(String pathName, FileMode fileMode) {
			super(prefix(pathName));
			mode = fileMode.getBits();

			final int s = pathName.lastIndexOf('/');
			final byte[] name = Constants.encode(pathName.substring(s + 1));
			ensurePathCapacity(pathOffset + name.length, pathOffset);
			System.arraycopy(name, 0, path, pathOffset, name.length);
			pathLen = pathOffset + name.length;
		}

		@Override
		public AbstractTreeIterator createSubtreeIterator(Repository repo)
				throws IncorrectObjectTypeException, IOException {
			return null;
		}
	}

	public void testPathCompare() throws Exception {
		assertTrue(new FakeTreeIterator("a", FileMode.REGULAR_FILE).pathCompare(
				new FakeTreeIterator("a", FileMode.TREE)) < 0);

		assertTrue(new FakeTreeIterator("a", FileMode.TREE).pathCompare(
				new FakeTreeIterator("a", FileMode.REGULAR_FILE)) > 0);

		assertTrue(new FakeTreeIterator("a", FileMode.REGULAR_FILE).pathCompare(
				new FakeTreeIterator("a", FileMode.REGULAR_FILE)) == 0);

		assertTrue(new FakeTreeIterator("a", FileMode.TREE).pathCompare(
				new FakeTreeIterator("a", FileMode.TREE)) == 0);
	}

	public void testGrowPath() throws Exception {
		final FakeTreeIterator i = new FakeTreeIterator("ab", FileMode.TREE);
		final byte[] origpath = i.path;
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');

		i.growPath(2);

		assertNotSame(origpath, i.path);
		assertEquals(origpath.length * 2, i.path.length);
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
	}

	public void testEnsurePathCapacityFastCase() throws Exception {
		final FakeTreeIterator i = new FakeTreeIterator("ab", FileMode.TREE);
		final int want = 50;
		final byte[] origpath = i.path;
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
		assertTrue(want < i.path.length);

		i.ensurePathCapacity(want, 2);

		assertSame(origpath, i.path);
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
	}

	public void testEnsurePathCapacityGrows() throws Exception {
		final FakeTreeIterator i = new FakeTreeIterator("ab", FileMode.TREE);
		final int want = 384;
		final byte[] origpath = i.path;
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
		assertTrue(i.path.length < want);

		i.ensurePathCapacity(want, 2);

		assertNotSame(origpath, i.path);
		assertEquals(512, i.path.length);
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
	}

	public void testEntryFileMode() {
		for (FileMode m : new FileMode[] { FileMode.TREE,
				FileMode.REGULAR_FILE, FileMode.EXECUTABLE_FILE,
				FileMode.GITLINK, FileMode.SYMLINK }) {
			final FakeTreeIterator i = new FakeTreeIterator("a", m);
			assertEquals(m.getBits(), i.getEntryRawMode());
			assertSame(m, i.getEntryFileMode());
		}
	}

	public void testEntryPath() {
		FakeTreeIterator i = new FakeTreeIterator("a/b/cd", FileMode.TREE);
		assertEquals("a/b/cd", i.getEntryPathString());
		assertEquals(2, i.getNameLength());
		byte[] b = new byte[3];
		b[0] = 0x0a;
		i.getName(b, 1);
		assertEquals(0x0a, b[0]);
		assertEquals('c', b[1]);
		assertEquals('d', b[2]);
	}

	public void testCreateEmptyTreeIterator() {
		FakeTreeIterator i = new FakeTreeIterator("a/b/cd", FileMode.TREE);
		EmptyTreeIterator e = i.createEmptyTreeIterator();
		assertNotNull(e);
		assertEquals(i.getEntryPathString() + "/", e.getEntryPathString());
	}
}
