/*
 * Copyright (C) 2015, Google Inc.
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
package org.eclipse.jgit.gitrepo;

import static org.junit.Assert.assertTrue;

import java.io.StringBufferInputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ManifestParserTest {

	@Test
	public void testManifestParser() throws Exception {
		String baseUrl = "https://git.google.com/";
		StringBuilder xmlContent = new StringBuilder();
		Set<String> results = new HashSet<String>();
		xmlContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<manifest>")
			.append("<remote name=\"remote1\" fetch=\".\" />")
			.append("<default revision=\"master\" remote=\"remote1\" />")
			.append("<project path=\"foo\" name=\"")
			.append("foo")
			.append("\" groups=\"a,test\" />")
			.append("<project path=\"bar\" name=\"")
			.append("bar")
			.append("\" groups=\"notdefault\" />")
			.append("<project path=\"foo/a\" name=\"")
			.append("a")
			.append("\" groups=\"a\" />")
			.append("<project path=\"b\" name=\"")
			.append("b")
			.append("\" groups=\"b\" />")
			.append("</manifest>");

		ManifestParser parser = new ManifestParser(
				null, null, "master", baseUrl, null, null);
		parser.read(new StringBufferInputStream(xmlContent.toString()));
		// Unfiltered projects should have them all.
		results.clear();
		results.add("foo");
		results.add("bar");
		results.add("foo/a");
		results.add("b");
		for (RepoProject proj : parser.getProjects()) {
			String msg = String.format(
					"project \"%s\" should be included in unfiltered projects",
					proj.getPath());
			assertTrue(msg, results.contains(proj.getPath()));
			results.remove(proj.getPath());
		}
		assertTrue(
				"Unfiltered projects shouldn't contain any unexpected results",
				results.isEmpty());
		// Filtered projects should have foo & b
		results.clear();
		results.add("foo");
		results.add("b");
		for (RepoProject proj : parser.getFilteredProjects()) {
			String msg = String.format(
					"project \"%s\" should be included in filtered projects",
					proj.getPath());
			assertTrue(msg, results.contains(proj.getPath()));
			results.remove(proj.getPath());
		}
		assertTrue(
				"Filtered projects shouldn't contain any unexpected results",
				results.isEmpty());
	}
}
