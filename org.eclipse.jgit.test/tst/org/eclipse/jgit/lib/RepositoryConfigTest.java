/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.util.Arrays;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;

/**
 * Test reading of git config
 */
public class RepositoryConfigTest extends TestCase {
	public void test001_ReadBareKey() throws ConfigInvalidException {
		final Config c = parse("[foo]\nbar\n");
		assertEquals(true, c.getBoolean("foo", null, "bar", false));
		assertEquals("", c.getString("foo", null, "bar"));
	}

	public void test002_ReadWithSubsection() throws ConfigInvalidException {
		final Config c = parse("[foo \"zip\"]\nbar\n[foo \"zap\"]\nbar=false\nn=3\n");
		assertEquals(true, c.getBoolean("foo", "zip", "bar", false));
		assertEquals("", c.getString("foo","zip", "bar"));
		assertEquals(false, c.getBoolean("foo", "zap", "bar", true));
		assertEquals("false", c.getString("foo", "zap", "bar"));
		assertEquals(3, c.getInt("foo", "zap", "n", 4));
		assertEquals(4, c.getInt("foo", "zap","m", 4));
	}

	public void test003_PutRemote() {
		final Config c = new Config();
		c.setString("sec", "ext", "name", "value");
		c.setString("sec", "ext", "name2", "value2");
		final String expText = "[sec \"ext\"]\n\tname = value\n\tname2 = value2\n";
		assertEquals(expText, c.toText());
	}

	public void test004_PutGetSimple() {
		Config c = new Config();
		c.setString("my", null, "somename", "false");
		assertEquals("false", c.getString("my", null, "somename"));
		assertEquals("[my]\n\tsomename = false\n", c.toText());
	}

	public void test005_PutGetStringList() {
		Config c = new Config();
		final LinkedList<String> values = new LinkedList<String>();
		values.add("value1");
		values.add("value2");
		c.setStringList("my", null, "somename", values);

		final Object[] expArr = values.toArray();
		final String[] actArr = c.getStringList("my", null, "somename");
		assertTrue(Arrays.equals(expArr, actArr));

		final String expText = "[my]\n\tsomename = value1\n\tsomename = value2\n";
		assertEquals(expText, c.toText());
	}

	public void test006_readCaseInsensitive() throws ConfigInvalidException {
		final Config c = parse("[Foo]\nBar\n");
		assertEquals(true, c.getBoolean("foo", null, "bar", false));
		assertEquals("", c.getString("foo", null, "bar"));
	}

	public void test007_readUserConfig() {
		final MockSystemReader mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);
		final String hostname = mockSystemReader.getHostname();
		final Config userGitConfig = mockSystemReader.openUserConfig();
		final Config localConfig = new Config(userGitConfig);
		mockSystemReader.clearProperties();

		String authorName;
		String authorEmail;

		// no values defined nowhere
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals(Constants.UNKNOWN_USER_DEFAULT, authorName);
		assertEquals(Constants.UNKNOWN_USER_DEFAULT + "@" + hostname, authorEmail);

		// the system user name is defined
		mockSystemReader.setProperty(Constants.OS_USER_NAME_KEY, "os user name");
		localConfig.uncache(UserConfig.KEY);
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		assertEquals("os user name", authorName);

		if (hostname != null && hostname.length() != 0) {
			authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
			assertEquals("os user name@" + hostname, authorEmail);
		}

		// the git environment variables are defined
		mockSystemReader.setProperty(Constants.GIT_AUTHOR_NAME_KEY, "git author name");
		mockSystemReader.setProperty(Constants.GIT_AUTHOR_EMAIL_KEY, "author@email");
		localConfig.uncache(UserConfig.KEY);
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals("git author name", authorName);
		assertEquals("author@email", authorEmail);

		// the values are defined in the global configuration
		userGitConfig.setString("user", null, "name", "global username");
		userGitConfig.setString("user", null, "email", "author@globalemail");
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals("global username", authorName);
		assertEquals("author@globalemail", authorEmail);

		// the values are defined in the local configuration
		localConfig.setString("user", null, "name", "local username");
		localConfig.setString("user", null, "email", "author@localemail");
		authorName = localConfig.get(UserConfig.KEY).getAuthorName();
		authorEmail = localConfig.get(UserConfig.KEY).getAuthorEmail();
		assertEquals("local username", authorName);
		assertEquals("author@localemail", authorEmail);

		authorName = localConfig.get(UserConfig.KEY).getCommitterName();
		authorEmail = localConfig.get(UserConfig.KEY).getCommitterEmail();
		assertEquals("local username", authorName);
		assertEquals("author@localemail", authorEmail);
	}

	public void testReadBoolean_TrueFalse1() throws ConfigInvalidException {
		final Config c = parse("[s]\na = true\nb = false\n");
		assertEquals("true", c.getString("s", null, "a"));
		assertEquals("false", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	public void testReadBoolean_TrueFalse2() throws ConfigInvalidException {
		final Config c = parse("[s]\na = TrUe\nb = fAlSe\n");
		assertEquals("TrUe", c.getString("s", null, "a"));
		assertEquals("fAlSe", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	public void testReadBoolean_YesNo1() throws ConfigInvalidException {
		final Config c = parse("[s]\na = yes\nb = no\n");
		assertEquals("yes", c.getString("s", null, "a"));
		assertEquals("no", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	public void testReadBoolean_YesNo2() throws ConfigInvalidException {
		final Config c = parse("[s]\na = yEs\nb = NO\n");
		assertEquals("yEs", c.getString("s", null, "a"));
		assertEquals("NO", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	public void testReadBoolean_OnOff1() throws ConfigInvalidException {
		final Config c = parse("[s]\na = on\nb = off\n");
		assertEquals("on", c.getString("s", null, "a"));
		assertEquals("off", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	public void testReadBoolean_OnOff2() throws ConfigInvalidException {
		final Config c = parse("[s]\na = ON\nb = OFF\n");
		assertEquals("ON", c.getString("s", null, "a"));
		assertEquals("OFF", c.getString("s", null, "b"));

		assertTrue(c.getBoolean("s", "a", false));
		assertFalse(c.getBoolean("s", "b", true));
	}

	public void testReadLong() throws ConfigInvalidException {
		assertReadLong(1L);
		assertReadLong(-1L);
		assertReadLong(Long.MIN_VALUE);
		assertReadLong(Long.MAX_VALUE);
		assertReadLong(4L * 1024 * 1024 * 1024, "4g");
		assertReadLong(3L * 1024 * 1024, "3 m");
		assertReadLong(8L * 1024, "8 k");

		try {
			assertReadLong(-1, "1.5g");
			fail("incorrectly accepted 1.5g");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid integer value: s.a=1.5g", e.getMessage());
		}
	}

	public void testBooleanWithNoValue() throws ConfigInvalidException {
		Config c = parse("[my]\n\tempty\n");
		assertEquals("", c.getString("my", null, "empty"));
		assertEquals(1, c.getStringList("my", null, "empty").length);
		assertEquals("", c.getStringList("my", null, "empty")[0]);
		assertTrue(c.getBoolean("my", "empty", false));
		assertEquals("[my]\n\tempty\n", c.toText());
	}

	public void testEmptyString() throws ConfigInvalidException {
		Config c = parse("[my]\n\tempty =\n");
		assertNull(c.getString("my", null, "empty"));

		String[] values = c.getStringList("my", null, "empty");
		assertNotNull(values);
		assertEquals(1, values.length);
		assertNull(values[0]);

		// always matches the default, because its non-boolean
		assertTrue(c.getBoolean("my", "empty", true));
		assertFalse(c.getBoolean("my", "empty", false));

		assertEquals("[my]\n\tempty =\n", c.toText());

		c = new Config();
		c.setStringList("my", null, "empty", Arrays.asList(values));
		assertEquals("[my]\n\tempty =\n", c.toText());
	}

	public void testUnsetBranchSection() throws ConfigInvalidException {
		Config c = parse("" //
				+ "[branch \"keep\"]\n"
				+ "  merge = master.branch.to.keep.in.the.file\n"
				+ "\n"
				+ "[branch \"remove\"]\n"
				+ "  merge = this.will.get.deleted\n"
				+ "  remote = origin-for-some-long-gone-place\n"
				+ "\n"
				+ "[core-section-not-to-remove-in-test]\n"
				+ "  packedGitLimit = 14\n");
		c.unsetSection("branch", "does.not.exist");
		c.unsetSection("branch", "remove");
		assertEquals("" //
				+ "[branch \"keep\"]\n"
				+ "  merge = master.branch.to.keep.in.the.file\n"
				+ "\n"
				+ "[core-section-not-to-remove-in-test]\n"
				+ "  packedGitLimit = 14\n", c.toText());
	}

	public void testUnsetSingleSection() throws ConfigInvalidException {
		Config c = parse("" //
				+ "[branch \"keep\"]\n"
				+ "  merge = master.branch.to.keep.in.the.file\n"
				+ "\n"
				+ "[single]\n"
				+ "  merge = this.will.get.deleted\n"
				+ "  remote = origin-for-some-long-gone-place\n"
				+ "\n"
				+ "[core-section-not-to-remove-in-test]\n"
				+ "  packedGitLimit = 14\n");
		c.unsetSection("single", null);
		assertEquals("" //
				+ "[branch \"keep\"]\n"
				+ "  merge = master.branch.to.keep.in.the.file\n"
				+ "\n"
				+ "[core-section-not-to-remove-in-test]\n"
				+ "  packedGitLimit = 14\n", c.toText());
	}

	private void assertReadLong(long exp) throws ConfigInvalidException {
		assertReadLong(exp, String.valueOf(exp));
	}

	private void assertReadLong(long exp, String act)
			throws ConfigInvalidException {
		final Config c = parse("[s]\na = " + act + "\n");
		assertEquals(exp, c.getLong("s", null, "a", 0L));
	}

	private Config parse(final String content) throws ConfigInvalidException {
		final Config c = new Config(null);
		c.fromText(content);
		return c;
	}
}
