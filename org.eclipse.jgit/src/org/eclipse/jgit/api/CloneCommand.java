/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;

/**
 * Clone a repository into a new working directory
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-clone.html"
 *      >Git documentation about Clone</a>
 */
public class CloneCommand implements Callable<Git> {

	private String uri;

	private File directory;

	private boolean bare;

	private String remote = Constants.DEFAULT_REMOTE_NAME;

	private String branch = Constants.HEAD;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private CredentialsProvider credentialsProvider;

	private int timeout;

	private boolean cloneAllBranches;

	private boolean noCheckout;

	private Collection<String> branchesToClone;

	/**
	 * Executes the {@code Clone} command.
	 *
	 * @throws JGitInternalException
	 *             if the repository can't be created
	 * @return the newly created {@code Git} object with associated repository
	 */
	public Git call() throws JGitInternalException {
		try {
			URIish u = new URIish(uri);
			Repository repository = init(u);
			FetchResult result = fetch(repository, u);
			if (!noCheckout)
				checkout(repository, result);
			return new Git(repository);
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		} catch (InvalidRemoteException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private Repository init(URIish u) {
		InitCommand command = Git.init();
		command.setBare(bare);
		if (directory == null)
			directory = new File(u.getHumanishName(), Constants.DOT_GIT);
		if (directory.exists() && directory.listFiles().length != 0)
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().cloneNonEmptyDirectory, directory.getName()));
		command.setDirectory(directory);
		return command.call().getRepository();
	}

	private FetchResult fetch(Repository repo, URIish u)
			throws URISyntaxException,
			JGitInternalException,
			InvalidRemoteException, IOException {
		// create the remote config and save it
		RemoteConfig config = new RemoteConfig(repo.getConfig(), remote);
		config.addURI(u);

		final String dst = bare ? Constants.R_HEADS : Constants.R_REMOTES
				+ config.getName();
		RefSpec refSpec = new RefSpec();
		refSpec = refSpec.setForceUpdate(true);
		refSpec = refSpec.setSourceDestination(Constants.R_HEADS + "*", dst + "/*"); //$NON-NLS-1$ //$NON-NLS-2$

		config.addFetchRefSpec(refSpec);
		config.update(repo.getConfig());

		repo.getConfig().save();

		// run the fetch command
		FetchCommand command = new FetchCommand(repo);
		command.setRemote(remote);
		command.setProgressMonitor(monitor);
		command.setTagOpt(TagOpt.FETCH_TAGS);
		command.setTimeout(timeout);
		if (credentialsProvider != null)
			command.setCredentialsProvider(credentialsProvider);

		List<RefSpec> specs = calculateRefSpecs(dst);
		command.setRefSpecs(specs);

		return command.call();
	}

	private List<RefSpec> calculateRefSpecs(final String dst) {
		RefSpec wcrs = new RefSpec();
		wcrs = wcrs.setForceUpdate(true);
		wcrs = wcrs.setSourceDestination(Constants.R_HEADS + "*", dst + "/*"); //$NON-NLS-1$ //$NON-NLS-2$
		List<RefSpec> specs = new ArrayList<RefSpec>();
		if (cloneAllBranches)
			specs.add(wcrs);
		else if (branchesToClone != null
				&& branchesToClone.size() > 0) {
			for (final String selectedRef : branchesToClone)
				if (wcrs.matchSource(selectedRef))
					specs.add(wcrs.expandFromSource(selectedRef));
		}
		return specs;
	}

	private void checkout(Repository repo, FetchResult result)
			throws JGitInternalException,
			MissingObjectException, IncorrectObjectTypeException, IOException {

		Ref head = result.getAdvertisedRef(branch);
		if (branch.equals(Constants.HEAD)) {
			Ref foundBranch = findBranchToCheckout(result);
			if (foundBranch != null)
				head = foundBranch;
		}

		if (head == null || head.getObjectId() == null)
			return; // throw exception?

		if (head.getName().startsWith(Constants.R_HEADS)) {
			final RefUpdate newHead = repo.updateRef(Constants.HEAD);
			newHead.disableRefLog();
			newHead.link(head.getName());
			addMergeConfig(repo, head);
		}

		final RevCommit commit = parseCommit(repo, head);

		boolean detached = !head.getName().startsWith(Constants.R_HEADS);
		RefUpdate u = repo.updateRef(Constants.HEAD, detached);
		u.setNewObjectId(commit.getId());
		u.forceUpdate();

		if (!bare) {
			DirCache dc = repo.lockDirCache();
			DirCacheCheckout co = new DirCacheCheckout(repo, dc,
					commit.getTree());
			co.checkout();
		}
	}

	private Ref findBranchToCheckout(FetchResult result) {
		Ref foundBranch = null;
		final Ref idHEAD = result.getAdvertisedRef(Constants.HEAD);
		for (final Ref r : result.getAdvertisedRefs()) {
			final String n = r.getName();
			if (!n.startsWith(Constants.R_HEADS))
				continue;
			if (idHEAD == null)
				continue;
			if (r.getObjectId().equals(idHEAD.getObjectId())) {
				foundBranch = r;
				break;
			}
		}
		return foundBranch;
	}

	private void addMergeConfig(Repository repo, Ref head) throws IOException {
		String branchName = Repository.shortenRefName(head.getName());
		repo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION,
				branchName, ConfigConstants.CONFIG_KEY_REMOTE, remote);
		repo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION,
				branchName, ConfigConstants.CONFIG_KEY_MERGE, head.getName());
		repo.getConfig().save();
	}

	private RevCommit parseCommit(final Repository repo, final Ref ref)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final RevWalk rw = new RevWalk(repo);
		final RevCommit commit;
		try {
			commit = rw.parseCommit(ref.getObjectId());
		} finally {
			rw.release();
		}
		return commit;
	}

	/**
	 * @param uri
	 *            the uri to clone from
	 * @return this instance
	 */
	public CloneCommand setURI(String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * The optional directory associated with the clone operation. If the
	 * directory isn't set, a name associated with the source uri will be used.
	 *
	 * @see URIish#getHumanishName()
	 *
	 * @param directory
	 *            the directory to clone to
	 * @return this instance
	 */
	public CloneCommand setDirectory(File directory) {
		this.directory = directory;
		return this;
	}

	/**
	 * @param bare
	 *            whether the cloned repository is bare or not
	 * @return this instance
	 */
	public CloneCommand setBare(boolean bare) {
		this.bare = bare;
		return this;
	}

	/**
	 * The remote name used to keep track of the upstream repository for the
	 * clone operation. If no remote name is set, the default value of
	 * <code>Constants.DEFAULT_REMOTE_NAME</code> will be used.
	 *
	 * @see Constants#DEFAULT_REMOTE_NAME
	 * @param remote
	 *            name that keeps track of the upstream repository
	 * @return this instance
	 */
	public CloneCommand setRemote(String remote) {
		this.remote = remote;
		return this;
	}

	/**
	 * @param branch
	 *            the initial branch to check out when cloning the repository
	 * @return this instance
	 */
	public CloneCommand setBranch(String branch) {
		this.branch = branch;
		return this;
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 *
	 * @param monitor
	 * @return {@code this}
	 */
	public CloneCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * @param credentialsProvider
	 *            the {@link CredentialsProvider} to use
	 * @return {@code this}
	 */
	public CloneCommand setCredentialsProvider(
			CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
		return this;
	}

	/**
	 * @param timeout
	 *            the timeout used for the fetch step
	 * @return {@code this}
	 */
	public CloneCommand setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * @param cloneAllBranches
	 *            true when all branches have to be fetched (indicates wildcard
	 *            in created fetch refspec), false otherwise.
	 * @return {@code this}
	 */
	public CloneCommand setCloneAllBranches(boolean cloneAllBranches) {
		this.cloneAllBranches = cloneAllBranches;
		return this;
	}

	/**
	 * @param branchesToClone
	 *            collection of branches to clone. Ignored when allSelected is
	 *            true.
	 * @return {@code this}
	 */
	public CloneCommand setBranchesToClone(Collection<String> branchesToClone) {
		this.branchesToClone = branchesToClone;
		return this;
	}

	/**
	 * @param noCheckout
	 *            if set to <code>true</code> no branch will be checked out
	 *            after the clone. This enhances performance of the clone
	 *            command when there is no need for a checked out branch.
	 * @return {@code this}
	 */
	public CloneCommand setNoCheckout(boolean noCheckout) {
		this.noCheckout = noCheckout;
		return this;
	}

}
