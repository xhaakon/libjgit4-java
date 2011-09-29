/*
 * Copyright (C) 2011, Christoph Brill <egore911@egore911.de>
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
package org.eclipse.jgit.api;

import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;

/**
 * The ls-remote command
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-ls-remote.html"
 *      >Git documentation about ls-remote</a>
 */
public class LsRemoteCommand extends GitCommand<Collection<Ref>> {

	private String remote = Constants.DEFAULT_REMOTE_NAME;

	private boolean heads;

	private boolean tags;

	private String uploadPack;

	/**
	 * @param repo
	 */
	public LsRemoteCommand(Repository repo) {
		super(repo);
	}

	/**
	 * The remote (uri or name) used for the fetch operation. If no remote is
	 * set, the default value of <code>Constants.DEFAULT_REMOTE_NAME</code> will
	 * be used.
	 *
	 * @see Constants#DEFAULT_REMOTE_NAME
	 * @param remote
	 * @return {@code this}
	 */
	public LsRemoteCommand setRemote(String remote) {
		checkCallable();
		this.remote = remote;
		return this;
	}

	/**
	 * Include refs/heads in references results
	 *
	 * @param heads
	 */
	public void setHeads(boolean heads) {
		this.heads = heads;
	}

	/**
	 * Include refs/tags in references results
	 *
	 * @param tags
	 */
	public void setTags(boolean tags) {
		this.tags = tags;
	}

	/**
	 * The full path of git-upload-pack on the remote host
	 *
	 * @param uploadPack
	 */
	public void setUploadPack(String uploadPack) {
		this.uploadPack = uploadPack;
	}

	public Collection<Ref> call() throws Exception {
		checkCallable();

		try {
			Transport transport = Transport.open(repo, remote);
			transport.setOptionUploadPack(uploadPack);

			try {
				Collection<RefSpec> refSpecs = new ArrayList<RefSpec>(1);
				if (tags)
					refSpecs.add(new RefSpec(
							"refs/tags/*:refs/remotes/origin/tags/*"));
				if (heads)
					refSpecs.add(new RefSpec(
							"refs/heads/*:refs/remotes/origin/*"));
				Collection<Ref> refs;
				Map<String, Ref> refmap = new HashMap<String, Ref>();
				FetchConnection fc = transport.openFetch();
				try {
					refs = fc.getRefs();
					if (refSpecs.isEmpty())
						for (Ref r : refs)
							refmap.put(r.getName(), r);
					else
						for (Ref r : refs)
							for (RefSpec rs : refSpecs)
								if (rs.matchSource(r)) {
									refmap.put(r.getName(), r);
									break;
								}
				} finally {
					fc.close();
				}
				return refmap.values();

			} catch (TransportException e) {
				throw new JGitInternalException(
						JGitText.get().exceptionCaughtDuringExecutionOfLsRemoteCommand,
						e);
			} finally {
				transport.close();
			}
		} catch (URISyntaxException e) {
			throw new InvalidRemoteException(MessageFormat.format(
					JGitText.get().invalidRemote, remote));
		} catch (NotSupportedException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfLsRemoteCommand,
					e);
		}
	}

}