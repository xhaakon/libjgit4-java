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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.junit.Test;

public class DirCacheTreeTest extends RepositoryTestCase {
	@Test
	public void testEmptyCache_NoCacheTree() throws Exception {
		final DirCache dc = db.readDirCache();
		assertNull(dc.getCacheTree(false));
	}

	@Test
	public void testEmptyCache_CreateEmptyCacheTree() throws Exception {
		final DirCache dc = db.readDirCache();
		final DirCacheTree tree = dc.getCacheTree(true);
		assertNotNull(tree);
		assertSame(tree, dc.getCacheTree(false));
		assertSame(tree, dc.getCacheTree(true));
		assertEquals("", tree.getNameString());
		assertEquals("", tree.getPathString());
		assertEquals(0, tree.getChildCount());
		assertEquals(0, tree.getEntrySpan());
		assertFalse(tree.isValid());
	}

	@Test
	public void testEmptyCache_Clear_NoCacheTree() throws Exception {
		final DirCache dc = db.readDirCache();
		final DirCacheTree tree = dc.getCacheTree(true);
		assertNotNull(tree);
		dc.clear();
		assertNull(dc.getCacheTree(false));
		assertNotSame(tree, dc.getCacheTree(true));
	}

	@Test
	public void testSingleSubtree() throws Exception {
		final DirCache dc = db.readDirCache();

		final String[] paths = { "a.", "a/b", "a/c", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}
		final int aFirst = 1;
		final int aLast = 3;

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		assertNull(dc.getCacheTree(false));
		final DirCacheTree root = dc.getCacheTree(true);
		assertNotNull(root);
		assertSame(root, dc.getCacheTree(true));
		assertEquals("", root.getNameString());
		assertEquals("", root.getPathString());
		assertEquals(1, root.getChildCount());
		assertEquals(dc.getEntryCount(), root.getEntrySpan());
		assertFalse(root.isValid());

		final DirCacheTree aTree = root.getChild(0);
		assertNotNull(aTree);
		assertSame(aTree, root.getChild(0));
		assertEquals("a", aTree.getNameString());
		assertEquals("a/", aTree.getPathString());
		assertEquals(0, aTree.getChildCount());
		assertEquals(aLast - aFirst + 1, aTree.getEntrySpan());
		assertFalse(aTree.isValid());
	}

	@Test
	public void testTwoLevelSubtree() throws Exception {
		final DirCache dc = db.readDirCache();

		final String[] paths = { "a.", "a/b", "a/c/e", "a/c/f", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}
		final int aFirst = 1;
		final int aLast = 4;
		final int acFirst = 2;
		final int acLast = 3;

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		assertNull(dc.getCacheTree(false));
		final DirCacheTree root = dc.getCacheTree(true);
		assertNotNull(root);
		assertSame(root, dc.getCacheTree(true));
		assertEquals("", root.getNameString());
		assertEquals("", root.getPathString());
		assertEquals(1, root.getChildCount());
		assertEquals(dc.getEntryCount(), root.getEntrySpan());
		assertFalse(root.isValid());

		final DirCacheTree aTree = root.getChild(0);
		assertNotNull(aTree);
		assertSame(aTree, root.getChild(0));
		assertEquals("a", aTree.getNameString());
		assertEquals("a/", aTree.getPathString());
		assertEquals(1, aTree.getChildCount());
		assertEquals(aLast - aFirst + 1, aTree.getEntrySpan());
		assertFalse(aTree.isValid());

		final DirCacheTree acTree = aTree.getChild(0);
		assertNotNull(acTree);
		assertSame(acTree, aTree.getChild(0));
		assertEquals("c", acTree.getNameString());
		assertEquals("a/c/", acTree.getPathString());
		assertEquals(0, acTree.getChildCount());
		assertEquals(acLast - acFirst + 1, acTree.getEntrySpan());
		assertFalse(acTree.isValid());
	}

	/**
	 * We had bugs related to buffer size in the DirCache. This test creates an
	 * index larger than the default BufferedInputStream buffer size. This made
	 * the DirCache unable to read the extensions when index size exceeded the
	 * buffer size (in some cases at least).
	 *
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	@Test
	public void testWriteReadTree() throws CorruptObjectException, IOException {
		final DirCache dc = db.lockDirCache();

		final String A = String.format("a%2000s", "a");
		final String B = String.format("b%2000s", "b");
		final String[] paths = { A + ".", A + "." + B, A + "/" + B, A + "0" + B };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);

		b.commit();
		DirCache read = db.readDirCache();

		assertEquals(paths.length, read.getEntryCount());
		assertEquals(1, read.getCacheTree(true).getChildCount());
	}
}
