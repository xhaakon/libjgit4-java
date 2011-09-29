/*
 * Copyright (C) 2009, Google Inc.
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

import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class RawParseUtils_MatchTest {
	@Test
	public void testMatch_Equal() {
		final byte[] src = Constants.encodeASCII(" differ\n");
		final byte[] dst = Constants.encodeASCII("foo differ\n");
		assertTrue(RawParseUtils.match(dst, 3, src) == 3 + src.length);
	}

	@Test
	public void testMatch_NotEqual() {
		final byte[] src = Constants.encodeASCII(" differ\n");
		final byte[] dst = Constants.encodeASCII("a differ\n");
		assertTrue(RawParseUtils.match(dst, 2, src) < 0);
	}

	@Test
	public void testMatch_Prefix() {
		final byte[] src = Constants.encodeASCII("author ");
		final byte[] dst = Constants.encodeASCII("author A. U. Thor");
		assertTrue(RawParseUtils.match(dst, 0, src) == src.length);
		assertTrue(RawParseUtils.match(dst, 1, src) < 0);
	}

	@Test
	public void testMatch_TooSmall() {
		final byte[] src = Constants.encodeASCII("author ");
		final byte[] dst = Constants.encodeASCII("author autho");
		assertTrue(RawParseUtils.match(dst, src.length + 1, src) < 0);
	}
}
