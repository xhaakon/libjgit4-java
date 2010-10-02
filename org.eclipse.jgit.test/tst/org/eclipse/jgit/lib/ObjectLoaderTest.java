/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.TestRng;

public class ObjectLoaderTest extends TestCase {
	private TestRng rng;

	protected void setUp() throws Exception {
		super.setUp();
		rng = new TestRng(getName());
	}

	public void testSmallObjectLoader() throws MissingObjectException,
			IOException {
		final byte[] act = rng.nextBytes(512);
		final ObjectLoader ldr = new ObjectLoader.SmallObject(OBJ_BLOB, act);

		assertEquals(OBJ_BLOB, ldr.getType());
		assertEquals(act.length, ldr.getSize());
		assertFalse("not is large", ldr.isLarge());
		assertSame(act, ldr.getCachedBytes());
		assertSame(act, ldr.getCachedBytes(1));
		assertSame(act, ldr.getCachedBytes(Integer.MAX_VALUE));

		byte[] copy = ldr.getBytes();
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		copy = ldr.getBytes(1);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		copy = ldr.getBytes(Integer.MAX_VALUE);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		ObjectStream in = ldr.openStream();
		assertNotNull("has stream", in);
		assertTrue("is small stream", in instanceof ObjectStream.SmallStream);
		assertEquals(OBJ_BLOB, in.getType());
		assertEquals(act.length, in.getSize());
		assertEquals(act.length, in.available());
		assertTrue("mark supported", in.markSupported());
		copy = new byte[act.length];
		assertEquals(act.length, in.read(copy));
		assertEquals(0, in.available());
		assertEquals(-1, in.read());
		assertTrue("same content", Arrays.equals(act, copy));

		ByteArrayOutputStream tmp = new ByteArrayOutputStream();
		ldr.copyTo(tmp);
		assertTrue("same content", Arrays.equals(act, tmp.toByteArray()));
	}

	public void testLargeObjectLoader() throws MissingObjectException,
			IOException {
		final byte[] act = rng.nextBytes(512);
		final ObjectLoader ldr = new ObjectLoader() {
			@Override
			public byte[] getCachedBytes() throws LargeObjectException {
				throw new LargeObjectException();
			}

			@Override
			public long getSize() {
				return act.length;
			}

			@Override
			public int getType() {
				return OBJ_BLOB;
			}

			@Override
			public ObjectStream openStream() throws MissingObjectException,
					IOException {
				return new ObjectStream.Filter(getType(), act.length,
						new ByteArrayInputStream(act));
			}
		};

		assertEquals(OBJ_BLOB, ldr.getType());
		assertEquals(act.length, ldr.getSize());
		assertTrue("is large", ldr.isLarge());

		try {
			ldr.getCachedBytes();
			fail("did not throw on getCachedBytes()");
		} catch (LargeObjectException tooBig) {
			// expected
		}

		try {
			ldr.getBytes();
			fail("did not throw on getBytes()");
		} catch (LargeObjectException tooBig) {
			// expected
		}

		try {
			ldr.getCachedBytes(64);
			fail("did not throw on getCachedBytes(64)");
		} catch (LargeObjectException tooBig) {
			// expected
		}

		byte[] copy = ldr.getCachedBytes(1024);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		ObjectStream in = ldr.openStream();
		assertNotNull("has stream", in);
		assertEquals(OBJ_BLOB, in.getType());
		assertEquals(act.length, in.getSize());
		assertEquals(act.length, in.available());
		assertTrue("mark supported", in.markSupported());
		copy = new byte[act.length];
		assertEquals(act.length, in.read(copy));
		assertEquals(0, in.available());
		assertEquals(-1, in.read());
		assertTrue("same content", Arrays.equals(act, copy));

		ByteArrayOutputStream tmp = new ByteArrayOutputStream();
		ldr.copyTo(tmp);
		assertTrue("same content", Arrays.equals(act, tmp.toByteArray()));
	}

	public void testLimitedGetCachedBytes() throws LargeObjectException,
			MissingObjectException, IOException {
		byte[] act = rng.nextBytes(512);
		ObjectLoader ldr = new ObjectLoader.SmallObject(OBJ_BLOB, act) {
			@Override
			public boolean isLarge() {
				return true;
			}
		};
		assertTrue("is large", ldr.isLarge());

		try {
			ldr.getCachedBytes(10);
			fail("Did not throw LargeObjectException");
		} catch (LargeObjectException tooBig) {
			// Expected result.
		}

		byte[] copy = ldr.getCachedBytes(512);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));

		copy = ldr.getCachedBytes(1024);
		assertNotSame(act, copy);
		assertTrue("same content", Arrays.equals(act, copy));
	}

	public void testLimitedGetCachedBytesExceedsJavaLimits()
			throws LargeObjectException, MissingObjectException, IOException {
		ObjectLoader ldr = new ObjectLoader() {
			@Override
			public boolean isLarge() {
				return true;
			}

			@Override
			public byte[] getCachedBytes() throws LargeObjectException {
				throw new LargeObjectException();
			}

			@Override
			public long getSize() {
				return Long.MAX_VALUE;
			}

			@Override
			public int getType() {
				return OBJ_BLOB;
			}

			@Override
			public ObjectStream openStream() throws MissingObjectException,
					IOException {
				return new ObjectStream() {
					@Override
					public long getSize() {
						return Long.MAX_VALUE;
					}

					@Override
					public int getType() {
						return OBJ_BLOB;
					}

					@Override
					public int read() throws IOException {
						fail("never should have reached read");
						return -1;
					}
				};
			}
		};
		assertTrue("is large", ldr.isLarge());

		try {
			ldr.getCachedBytes(10);
			fail("Did not throw LargeObjectException");
		} catch (LargeObjectException tooBig) {
			// Expected result.
		}

		try {
			ldr.getCachedBytes(Integer.MAX_VALUE);
			fail("Did not throw LargeObjectException");
		} catch (LargeObjectException tooBig) {
			// Expected result.
		}
	}
}
