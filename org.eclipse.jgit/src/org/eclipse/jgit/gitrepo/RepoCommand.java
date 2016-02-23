/*
 * Copyright (C) 2014, Google Inc.
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

import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.SubmoduleAddCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.gitrepo.ManifestParser.IncludedFileReader;
import org.eclipse.jgit.gitrepo.RepoProject.CopyFile;
import org.eclipse.jgit.gitrepo.internal.RepoText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FileUtils;

/**
 * A class used to execute a repo command.
 *
 * This will parse a repo XML manifest, convert it into .gitmodules file and the
 * repository config file.
 *
 * If called against a bare repository, it will replace all the existing content
 * of the repository with the contents populated from the manifest.
 *
 * repo manifest allows projects overlapping, e.g. one project's path is
 * &quot;foo&quot; and another project's path is &quot;foo/bar&quot;. This won't
 * work in git submodule, so we'll skip all the sub projects
 * (&quot;foo/bar&quot; in the example) while converting.
 *
 * @see <a href="https://code.google.com/p/git-repo/">git-repo project page</a>
 * @since 3.4
 */
public class RepoCommand extends GitCommand<RevCommit> {

	private String path;
	private String uri;
	private String groups;
	private String branch;
	private String targetBranch = Constants.HEAD;
	private boolean recordRemoteBranch = false;
	private PersonIdent author;
	private RemoteReader callback;
	private InputStream inputStream;
	private IncludedFileReader includedReader;

	private List<RepoProject> bareProjects;
	private Git git;
	private ProgressMonitor monitor;

	/**
	 * A callback to get ref sha1 of a repository from its uri.
	 *
	 * We provided a default implementation {@link DefaultRemoteReader} to
	 * use ls-remote command to read the sha1 from the repository and clone the
	 * repository to read the file. Callers may have their own quicker
	 * implementation.
	 *
	 * @since 3.4
	 */
	public interface RemoteReader {
		/**
		 * Read a remote ref sha1.
		 *
		 * @param uri
		 *            The URI of the remote repository
		 * @param ref
		 *            The ref (branch/tag/etc.) to read
		 * @return the sha1 of the remote repository
		 * @throws GitAPIException
		 */
		public ObjectId sha1(String uri, String ref) throws GitAPIException;

		/**
		 * Read a file from a remote repository.
		 *
		 * @param uri
		 *            The URI of the remote repository
		 * @param ref
		 *            The ref (branch/tag/etc.) to read
		 * @param path
		 *            The relative path (inside the repo) to the file to read
		 * @return the file content.
		 * @throws GitAPIException
		 * @throws IOException
		 * @since 3.5
		 */
		public byte[] readFile(String uri, String ref, String path)
				throws GitAPIException, IOException;
	}

	/** A default implementation of {@link RemoteReader} callback. */
	public static class DefaultRemoteReader implements RemoteReader {
		public ObjectId sha1(String uri, String ref) throws GitAPIException {
			Map<String, Ref> map = Git
					.lsRemoteRepository()
					.setRemote(uri)
					.callAsMap();
			Ref r = RefDatabase.findRef(map, ref);
			return r != null ? r.getObjectId() : null;
		}

		public byte[] readFile(String uri, String ref, String path)
				throws GitAPIException, IOException {
			File dir = FileUtils.createTempDir("jgit_", ".git", null); //$NON-NLS-1$ //$NON-NLS-2$
			Repository repo = Git
					.cloneRepository()
					.setBare(true)
					.setDirectory(dir)
					.setURI(uri)
					.call()
					.getRepository();
			try {
				return readFileFromRepo(repo, ref, path);
			} finally {
				repo.close();
				FileUtils.delete(dir, FileUtils.RECURSIVE);
			}
		}

		/**
		 * Read a file from the repository
		 *
		 * @param repo
		 *            The repository containing the file
		 * @param ref
		 *            The ref (branch/tag/etc.) to read
		 * @param path
		 *            The relative path (inside the repo) to the file to read
		 * @return the file's content
		 * @throws GitAPIException
		 * @throws IOException
		 * @since 3.5
		 */
		protected byte[] readFileFromRepo(Repository repo,
				String ref, String path) throws GitAPIException, IOException {
			try (ObjectReader reader = repo.newObjectReader()) {
				ObjectId oid = repo.resolve(ref + ":" + path); //$NON-NLS-1$
				return reader.open(oid).getBytes(Integer.MAX_VALUE);
			}
		}
	}

	@SuppressWarnings("serial")
	private static class ManifestErrorException extends GitAPIException {
		ManifestErrorException(Throwable cause) {
			super(RepoText.get().invalidManifest, cause);
		}
	}

	@SuppressWarnings("serial")
	private static class RemoteUnavailableException extends GitAPIException {
		RemoteUnavailableException(String uri) {
			super(MessageFormat.format(RepoText.get().errorRemoteUnavailable, uri));
		}
	}

	/**
	 * @param repo
	 */
	public RepoCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set path to the manifest XML file.
	 * <p>
	 * Calling {@link #setInputStream} will ignore the path set here.
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public RepoCommand setPath(String path) {
		this.path = path;
		return this;
	}

	/**
	 * Set the input stream to the manifest XML.
	 * <p>
	 * Setting inputStream will ignore the path set. It will be closed in
	 * {@link #call}.
	 *
	 * @param inputStream
	 * @return this command
	 * @since 3.5
	 */
	public RepoCommand setInputStream(InputStream inputStream) {
		this.inputStream = inputStream;
		return this;
	}

	/**
	 * Set base URI of the pathes inside the XML
	 *
	 * @param uri
	 * @return this command
	 */
	public RepoCommand setURI(String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * Set groups to sync
	 *
	 * @param groups groups separated by comma, examples: default|all|G1,-G2,-G3
	 * @return this command
	 */
	public RepoCommand setGroups(String groups) {
		this.groups = groups;
		return this;
	}

	/**
	 * Set default branch.
	 * <p>
	 * This is generally the name of the branch the manifest file was in. If
	 * there's no default revision (branch) specified in manifest and no
	 * revision specified in project, this branch will be used.
	 *
	 * @param branch
	 * @return this command
	 */
	public RepoCommand setBranch(String branch) {
		this.branch = branch;
		return this;
	}

	/**
	 * Set target branch.
	 * <p>
	 * This is the target branch of the super project to be updated. If not set,
	 * default is HEAD.
	 * <p>
	 * For non-bare repositories, HEAD will always be used and this will be
	 * ignored.
	 *
	 * @param branch
	 * @return this command
	 * @since 4.1
	 */
	public RepoCommand setTargetBranch(String branch) {
		this.targetBranch = Constants.R_HEADS + branch;
		return this;
	}

	/**
	 * Set whether the branch name should be recorded in .gitmodules
	 * <p>
	 * Submodule entries in .gitmodules can include a "branch" field
	 * to indicate what remote branch each submodule tracks.
	 * <p>
	 * That field is used by "git submodule update --remote" to update
	 * to the tip of the tracked branch when asked and by Gerrit to
	 * update the superproject when a change on that branch is merged.
	 * <p>
	 * Subprojects that request a specific commit or tag will not have
	 * a branch name recorded.
	 * <p>
	 * Not implemented for non-bare repositories.
	 *
	 * @param enable Whether to record the branch name
	 * @return this command
	 * @since 4.2
	 */
	public RepoCommand setRecordRemoteBranch(boolean enable) {
		this.recordRemoteBranch = enable;
		return this;
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see org.eclipse.jgit.lib.NullProgressMonitor
	 * @param monitor
	 * @return this command
	 */
	public RepoCommand setProgressMonitor(final ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * Set the author/committer for the bare repository commit.
	 * <p>
	 * For non-bare repositories, the current user will be used and this will be
	 * ignored.
	 *
	 * @param author
	 * @return this command
	 */
	public RepoCommand setAuthor(final PersonIdent author) {
		this.author = author;
		return this;
	}

	/**
	 * Set the GetHeadFromUri callback.
	 *
	 * This is only used in bare repositories.
	 *
	 * @param callback
	 * @return this command
	 */
	public RepoCommand setRemoteReader(final RemoteReader callback) {
		this.callback = callback;
		return this;
	}

	/**
	 * Set the IncludedFileReader callback.
	 *
	 * @param reader
	 * @return this command
	 * @since 4.0
	 */
	public RepoCommand setIncludedFileReader(IncludedFileReader reader) {
		this.includedReader = reader;
		return this;
	}

	@Override
	public RevCommit call() throws GitAPIException {
		try {
			checkCallable();
			if (uri == null || uri.length() == 0)
				throw new IllegalArgumentException(
						JGitText.get().uriNotConfigured);
			if (inputStream == null) {
				if (path == null || path.length() == 0)
					throw new IllegalArgumentException(
							JGitText.get().pathNotConfigured);
				try {
					inputStream = new FileInputStream(path);
				} catch (IOException e) {
					throw new IllegalArgumentException(
							JGitText.get().pathNotConfigured);
				}
			}

			if (repo.isBare()) {
				bareProjects = new ArrayList<RepoProject>();
				if (author == null)
					author = new PersonIdent(repo);
				if (callback == null)
					callback = new DefaultRemoteReader();
			} else
				git = new Git(repo);

			ManifestParser parser = new ManifestParser(
					includedReader, path, branch, uri, groups, repo);
			try {
				parser.read(inputStream);
				for (RepoProject proj : parser.getFilteredProjects()) {
					addSubmodule(proj.getUrl(),
							proj.getPath(),
							proj.getRevision(),
							proj.getCopyFiles());
				}
			} catch (GitAPIException | IOException e) {
				throw new ManifestErrorException(e);
			}
		} finally {
			try {
				if (inputStream != null)
					inputStream.close();
			} catch (IOException e) {
				// Just ignore it, it's not important.
			}
		}

		if (repo.isBare()) {
			DirCache index = DirCache.newInCore();
			DirCacheBuilder builder = index.builder();
			ObjectInserter inserter = repo.newObjectInserter();
			try (RevWalk rw = new RevWalk(repo)) {
				Config cfg = new Config();
				for (RepoProject proj : bareProjects) {
					String name = proj.getPath();
					String nameUri = proj.getName();
					cfg.setString("submodule", name, "path", name); //$NON-NLS-1$ //$NON-NLS-2$
					cfg.setString("submodule", name, "url", nameUri); //$NON-NLS-1$ //$NON-NLS-2$
					// create gitlink
					DirCacheEntry dcEntry = new DirCacheEntry(name);
					ObjectId objectId;
					if (ObjectId.isId(proj.getRevision())) {
						objectId = ObjectId.fromString(proj.getRevision());
					} else {
						objectId = callback.sha1(nameUri, proj.getRevision());
						if (recordRemoteBranch)
							// can be branch or tag
							cfg.setString("submodule", name, "branch", //$NON-NLS-1$ //$NON-NLS-2$
									proj.getRevision());
					}
					if (objectId == null)
						throw new RemoteUnavailableException(nameUri);
					dcEntry.setObjectId(objectId);
					dcEntry.setFileMode(FileMode.GITLINK);
					builder.add(dcEntry);

					for (CopyFile copyfile : proj.getCopyFiles()) {
						byte[] src = callback.readFile(
								nameUri, proj.getRevision(), copyfile.src);
						objectId = inserter.insert(Constants.OBJ_BLOB, src);
						dcEntry = new DirCacheEntry(copyfile.dest);
						dcEntry.setObjectId(objectId);
						dcEntry.setFileMode(FileMode.REGULAR_FILE);
						builder.add(dcEntry);
					}
				}
				String content = cfg.toText();

				// create a new DirCacheEntry for .gitmodules file.
				final DirCacheEntry dcEntry = new DirCacheEntry(Constants.DOT_GIT_MODULES);
				ObjectId objectId = inserter.insert(Constants.OBJ_BLOB,
						content.getBytes(Constants.CHARACTER_ENCODING));
				dcEntry.setObjectId(objectId);
				dcEntry.setFileMode(FileMode.REGULAR_FILE);
				builder.add(dcEntry);

				builder.finish();
				ObjectId treeId = index.writeTree(inserter);

				// Create a Commit object, populate it and write it
				ObjectId headId = repo.resolve(targetBranch + "^{commit}"); //$NON-NLS-1$
				CommitBuilder commit = new CommitBuilder();
				commit.setTreeId(treeId);
				if (headId != null)
					commit.setParentIds(headId);
				commit.setAuthor(author);
				commit.setCommitter(author);
				commit.setMessage(RepoText.get().repoCommitMessage);

				ObjectId commitId = inserter.insert(commit);
				inserter.flush();

				RefUpdate ru = repo.updateRef(targetBranch);
				ru.setNewObjectId(commitId);
				ru.setExpectedOldObjectId(headId != null ? headId : ObjectId.zeroId());
				Result rc = ru.update(rw);

				switch (rc) {
					case NEW:
					case FORCED:
					case FAST_FORWARD:
						// Successful. Do nothing.
						break;
					case REJECTED:
					case LOCK_FAILURE:
						throw new ConcurrentRefUpdateException(
								MessageFormat.format(
										JGitText.get().cannotLock, targetBranch),
								ru.getRef(),
								rc);
					default:
						throw new JGitInternalException(MessageFormat.format(
								JGitText.get().updatingRefFailed,
								targetBranch, commitId.name(), rc));
				}

				return rw.parseCommit(commitId);
			} catch (IOException e) {
				throw new ManifestErrorException(e);
			}
		} else {
			return git
				.commit()
				.setMessage(RepoText.get().repoCommitMessage)
				.call();
		}
	}

	private void addSubmodule(String url, String name, String revision,
			List<CopyFile> copyfiles) throws GitAPIException, IOException {
		if (repo.isBare()) {
			RepoProject proj = new RepoProject(url, name, revision, null, null);
			proj.addCopyFiles(copyfiles);
			bareProjects.add(proj);
		} else {
			SubmoduleAddCommand add = git
				.submoduleAdd()
				.setPath(name)
				.setURI(url);
			if (monitor != null)
				add.setProgressMonitor(monitor);

			Repository subRepo = add.call();
			if (revision != null) {
				try (Git sub = new Git(subRepo)) {
					sub.checkout().setName(findRef(revision, subRepo))
							.call();
				}
				subRepo.close();
				git.add().addFilepattern(name).call();
			}
			for (CopyFile copyfile : copyfiles) {
				copyfile.copy();
				git.add().addFilepattern(copyfile.dest).call();
			}
		}
	}

	private static String findRef(String ref, Repository repo)
			throws IOException {
		if (!ObjectId.isId(ref)) {
			Ref r = repo.exactRef(R_REMOTES + DEFAULT_REMOTE_NAME + "/" + ref); //$NON-NLS-1$
			if (r != null)
				return r.getName();
		}
		return ref;
	}
}
