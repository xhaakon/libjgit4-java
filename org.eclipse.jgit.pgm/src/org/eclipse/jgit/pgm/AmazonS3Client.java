/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.pgm;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.Properties;

import org.eclipse.jgit.transport.AmazonS3;
import org.kohsuke.args4j.Argument;

@Command(name = "amazon-s3-client", common = false, usage = "Command line client for Amazon's S3 service")
class AmazonS3Client extends TextBuiltin {
	@Argument(index = 0, metaVar = "conn.prop", required = true)
	private File propertyFile;

	@Argument(index = 1, metaVar = "OP", required = true)
	private String op;

	@Argument(index = 2, metaVar = "BUCKET", required = true)
	private String bucket;

	@Argument(index = 3, metaVar = "KEY", required = true)
	private String key;

	@Override
	protected final boolean requiresRepository() {
		return false;
	}

	@Override
	protected void run() throws Exception {
		final AmazonS3 s3 = new AmazonS3(properties());

		if ("get".equals(op)) {
			final URLConnection c = s3.get(bucket, key);
			int len = c.getContentLength();
			final InputStream in = c.getInputStream();
			try {
				final byte[] tmp = new byte[2048];
				while (len > 0) {
					final int n = in.read(tmp);
					if (n < 0)
						throw new EOFException("Expected " + len + " bytes.");
					System.out.write(tmp, 0, n);
					len -= n;
				}
			} finally {
				in.close();
			}

		} else if ("ls".equals(op) || "list".equals(op)) {
			for (final String k : s3.list(bucket, key))
				System.out.println(k);

		} else if ("rm".equals(op) || "delete".equals(op)) {
			s3.delete(bucket, key);

		} else if ("put".equals(op)) {
			final OutputStream os = s3.beginPut(bucket, key, null, null);
			final byte[] tmp = new byte[2048];
			int n;
			while ((n = System.in.read(tmp)) > 0)
				os.write(tmp, 0, n);
			os.close();

		} else {
			throw die("Unsupported operation: " + op);
		}
	}

	private Properties properties() {
		try {
			final InputStream in = new FileInputStream(propertyFile);
			try {
				final Properties p = new Properties();
				p.load(in);
				return p;
			} finally {
				in.close();
			}
		} catch (FileNotFoundException e) {
			throw die("no such file: " + propertyFile, e);
		} catch (IOException e) {
			throw die("cannot read " + propertyFile, e);
		}
	}
}
