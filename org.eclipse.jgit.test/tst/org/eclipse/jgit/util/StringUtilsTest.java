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

import junit.framework.TestCase;

public class StringUtilsTest extends TestCase {
	public void testToLowerCaseChar() {
		assertEquals('a', StringUtils.toLowerCase('A'));
		assertEquals('z', StringUtils.toLowerCase('Z'));

		assertEquals('a', StringUtils.toLowerCase('a'));
		assertEquals('z', StringUtils.toLowerCase('z'));

		assertEquals((char) 0, StringUtils.toLowerCase((char) 0));
		assertEquals((char) 0xffff, StringUtils.toLowerCase((char) 0xffff));
	}

	public void testToLowerCaseString() {
		assertEquals("\n abcdefghijklmnopqrstuvwxyz\n", StringUtils
				.toLowerCase("\n ABCDEFGHIJKLMNOPQRSTUVWXYZ\n"));
	}

	public void testEqualsIgnoreCase1() {
		final String a = "FOO";
		assertTrue(StringUtils.equalsIgnoreCase(a, a));
	}

	public void testEqualsIgnoreCase2() {
		assertFalse(StringUtils.equalsIgnoreCase("a", ""));
	}

	public void testEqualsIgnoreCase3() {
		assertFalse(StringUtils.equalsIgnoreCase("a", "b"));
		assertFalse(StringUtils.equalsIgnoreCase("ac", "ab"));
	}

	public void testEqualsIgnoreCase4() {
		assertTrue(StringUtils.equalsIgnoreCase("a", "a"));
		assertTrue(StringUtils.equalsIgnoreCase("A", "a"));
		assertTrue(StringUtils.equalsIgnoreCase("a", "A"));
	}
}
