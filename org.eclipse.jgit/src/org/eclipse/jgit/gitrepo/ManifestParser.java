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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.gitrepo.RepoProject.CopyFile;
import org.eclipse.jgit.gitrepo.internal.RepoText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Repo XML manifest parser.
 *
 * @see <a href="https://code.google.com/p/git-repo/">git-repo project page</a>
 * @since 4.0
 */
public class ManifestParser extends DefaultHandler {
	private final String filename;
	private final String baseUrl;
	private final String defaultBranch;
	private final Repository rootRepo;
	private final Map<String, String> remotes;
	private final Set<String> plusGroups;
	private final Set<String> minusGroups;
	private final List<RepoProject> projects;
	private final List<RepoProject> filteredProjects;
	private final IncludedFileReader includedReader;

	private String defaultRemote;
	private String defaultRevision;
	private int xmlInRead;
	private RepoProject currentProject;

	/**
	 * A callback to read included xml files.
	 */
	public interface IncludedFileReader {
		/**
		 * Read a file from the same base dir of the manifest xml file.
		 *
		 * @param path
		 *            The relative path to the file to read
		 * @return the {@code InputStream} of the file.
		 * @throws GitAPIException
		 * @throws IOException
		 */
		public InputStream readIncludeFile(String path)
				throws GitAPIException, IOException;
	}

	/**
	 * @param includedReader
	 * @param filename
	 * @param defaultBranch
	 * @param baseUrl
	 * @param groups
	 * @param rootRepo
	 */
	public ManifestParser(IncludedFileReader includedReader, String filename,
			String defaultBranch, String baseUrl, String groups,
			Repository rootRepo) {
		this.includedReader = includedReader;
		this.filename = filename;
		this.defaultBranch = defaultBranch;
		this.rootRepo = rootRepo;

		// Strip trailing /s to match repo behavior.
		int lastIndex = baseUrl.length() - 1;
		while (lastIndex >= 0 && baseUrl.charAt(lastIndex) == '/')
			lastIndex--;
		this.baseUrl = baseUrl.substring(0, lastIndex + 1);

		plusGroups = new HashSet<String>();
		minusGroups = new HashSet<String>();
		if (groups == null || groups.length() == 0
				|| groups.equals("default")) { //$NON-NLS-1$
			// default means "all,-notdefault"
			minusGroups.add("notdefault"); //$NON-NLS-1$
		} else {
			for (String group : groups.split(",")) { //$NON-NLS-1$
				if (group.startsWith("-")) //$NON-NLS-1$
					minusGroups.add(group.substring(1));
				else
					plusGroups.add(group);
			}
		}

		remotes = new HashMap<String, String>();
		projects = new ArrayList<RepoProject>();
		filteredProjects = new ArrayList<RepoProject>();
	}

	/**
	 * Read the xml file.
	 *
	 * @param inputStream
	 * @throws IOException
	 */
	public void read(InputStream inputStream) throws IOException {
		xmlInRead++;
		final XMLReader xr;
		try {
			xr = XMLReaderFactory.createXMLReader();
		} catch (SAXException e) {
			throw new IOException(JGitText.get().noXMLParserAvailable);
		}
		xr.setContentHandler(this);
		try {
			xr.parse(new InputSource(inputStream));
		} catch (SAXException e) {
			IOException error = new IOException(
						RepoText.get().errorParsingManifestFile);
			error.initCause(e);
			throw error;
		}
	}

	@Override
	public void startElement(
			String uri,
			String localName,
			String qName,
			Attributes attributes) throws SAXException {
		if ("project".equals(qName)) { //$NON-NLS-1$
			if (attributes.getValue("name") == null) { //$NON-NLS-1$
				throw new SAXException(RepoText.get().invalidManifest);
			}
			currentProject = new RepoProject(
					attributes.getValue("name"), //$NON-NLS-1$
					attributes.getValue("path"), //$NON-NLS-1$
					attributes.getValue("revision"), //$NON-NLS-1$
					attributes.getValue("remote"), //$NON-NLS-1$
					attributes.getValue("groups")); //$NON-NLS-1$
		} else if ("remote".equals(qName)) { //$NON-NLS-1$
			String alias = attributes.getValue("alias"); //$NON-NLS-1$
			String fetch = attributes.getValue("fetch"); //$NON-NLS-1$
			remotes.put(attributes.getValue("name"), fetch); //$NON-NLS-1$
			if (alias != null)
				remotes.put(alias, fetch);
		} else if ("default".equals(qName)) { //$NON-NLS-1$
			defaultRemote = attributes.getValue("remote"); //$NON-NLS-1$
			defaultRevision = attributes.getValue("revision"); //$NON-NLS-1$
			if (defaultRevision == null)
				defaultRevision = defaultBranch;
		} else if ("copyfile".equals(qName)) { //$NON-NLS-1$
			if (currentProject == null)
				throw new SAXException(RepoText.get().invalidManifest);
			currentProject.addCopyFile(new CopyFile(
						rootRepo,
						currentProject.getPath(),
						attributes.getValue("src"), //$NON-NLS-1$
						attributes.getValue("dest"))); //$NON-NLS-1$
		} else if ("include".equals(qName)) { //$NON-NLS-1$
			String name = attributes.getValue("name"); //$NON-NLS-1$
			InputStream is = null;
			if (includedReader != null) {
				try {
					is = includedReader.readIncludeFile(name);
				} catch (Exception e) {
					throw new SAXException(MessageFormat.format(
							RepoText.get().errorIncludeFile, name), e);
				}
			} else if (filename != null) {
				int index = filename.lastIndexOf('/');
				String path = filename.substring(0, index + 1) + name;
				try {
					is = new FileInputStream(path);
				} catch (IOException e) {
					throw new SAXException(MessageFormat.format(
							RepoText.get().errorIncludeFile, path), e);
				}
			}
			if (is == null) {
				throw new SAXException(
						RepoText.get().errorIncludeNotImplemented);
			}
			try {
				read(is);
			} catch (IOException e) {
				throw new SAXException(e);
			}
		}
	}

	@Override
	public void endElement(
			String uri,
			String localName,
			String qName) throws SAXException {
		if ("project".equals(qName)) { //$NON-NLS-1$
			projects.add(currentProject);
			currentProject = null;
		}
	}

	@Override
	public void endDocument() throws SAXException {
		xmlInRead--;
		if (xmlInRead != 0)
			return;

		// Only do the following after we finished reading everything.
		Map<String, String> remoteUrls = new HashMap<String, String>();
		URI baseUri;
		try {
			baseUri = new URI(baseUrl);
		} catch (URISyntaxException e) {
			throw new SAXException(e);
		}
		for (RepoProject proj : projects) {
			String remote = proj.getRemote();
			if (remote == null) {
				if (defaultRemote == null) {
					if (filename != null)
						throw new SAXException(MessageFormat.format(
								RepoText.get().errorNoDefaultFilename,
								filename));
					else
						throw new SAXException(
								RepoText.get().errorNoDefault);
				}
				remote = defaultRemote;
			}
			String remoteUrl = remoteUrls.get(remote);
			if (remoteUrl == null) {
				remoteUrl = baseUri.resolve(remotes.get(remote)).toString();
				if (!remoteUrl.endsWith("/")) //$NON-NLS-1$
					remoteUrl = remoteUrl + "/"; //$NON-NLS-1$
				remoteUrls.put(remote, remoteUrl);
			}
			proj.setUrl(remoteUrl + proj.getName())
					.setDefaultRevision(defaultRevision);
		}

		filteredProjects.addAll(projects);
		removeNotInGroup();
		removeOverlaps();
	}

	/**
	 * Getter for projects.
	 *
	 * @return projects list reference, never null
	 */
	public List<RepoProject> getProjects() {
		return projects;
	}

	/**
	 * Getter for filterdProjects.
	 *
	 * @return filtered projects list reference, never null
	 */
	public List<RepoProject> getFilteredProjects() {
		return filteredProjects;
	}

	/** Remove projects that are not in our desired groups. */
	void removeNotInGroup() {
		Iterator<RepoProject> iter = filteredProjects.iterator();
		while (iter.hasNext())
			if (!inGroups(iter.next()))
				iter.remove();
	}

	/** Remove projects that sits in a subdirectory of any other project. */
	void removeOverlaps() {
		Collections.sort(filteredProjects);
		Iterator<RepoProject> iter = filteredProjects.iterator();
		if (!iter.hasNext())
			return;
		RepoProject last = iter.next();
		while (iter.hasNext()) {
			RepoProject p = iter.next();
			if (last.isAncestorOf(p))
				iter.remove();
			else
				last = p;
		}
		removeNestedCopyfiles();
	}

	/** Remove copyfiles that sit in a subdirectory of any other project. */
	void removeNestedCopyfiles() {
		for (RepoProject proj : filteredProjects) {
			List<CopyFile> copyfiles = new ArrayList<>(proj.getCopyFiles());
			proj.clearCopyFiles();
			for (CopyFile copyfile : copyfiles) {
				if (!isNestedCopyfile(copyfile)) {
					proj.addCopyFile(copyfile);
				}
			}
		}
	}

	boolean inGroups(RepoProject proj) {
		for (String group : minusGroups) {
			if (proj.inGroup(group)) {
				// minus groups have highest priority.
				return false;
			}
		}
		if (plusGroups.isEmpty() || plusGroups.contains("all")) { //$NON-NLS-1$
			// empty plus groups means "all"
			return true;
		}
		for (String group : plusGroups) {
			if (proj.inGroup(group))
				return true;
		}
		return false;
	}

	private boolean isNestedCopyfile(CopyFile copyfile) {
		if (copyfile.dest.indexOf('/') == -1) {
			// If the copyfile is at root level then it won't be nested.
			return false;
		}
		for (RepoProject proj : filteredProjects) {
			if (proj.getPath().compareTo(copyfile.dest) > 0) {
				// Early return as remaining projects can't be ancestor of this
				// copyfile config (filteredProjects is sorted).
				return false;
			}
			if (proj.isAncestorOf(copyfile.dest)) {
				return true;
			}
		}
		return false;
	}
}
