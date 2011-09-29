/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

/**
 * Constants for use with the Configuration classes: section names,
 * configuration keys
 */
public class ConfigConstants {
	/** The "core" section */
	public static final String CONFIG_CORE_SECTION = "core";

	/** The "branch" section */
	public static final String CONFIG_BRANCH_SECTION = "branch";

	/** The "remote" section */
	public static final String CONFIG_REMOTE_SECTION = "remote";

	/** The "diff" section */
	public static final String CONFIG_DIFF_SECTION = "diff";

	/** The "user" section */
	public static final String CONFIG_USER_SECTION = "user";

	/** The "gerrit" section */
	public static final String CONFIG_GERRIT_SECTION = "gerrit";

	/** The "algorithm" key */
	public static final String CONFIG_KEY_ALGORITHM = "algorithm";

	/** The "autocrlf" key */
	public static final String CONFIG_KEY_AUTOCRLF = "autocrlf";

	/** The "bare" key */
	public static final String CONFIG_KEY_BARE = "bare";

	/** The "excludesfile" key */
	public static final String CONFIG_KEY_EXCLUDESFILE = "excludesfile";

	/** The "filemode" key */
	public static final String CONFIG_KEY_FILEMODE = "filemode";

	/** The "logallrefupdates" key */
	public static final String CONFIG_KEY_LOGALLREFUPDATES = "logallrefupdates";

	/** The "repositoryformatversion" key */
	public static final String CONFIG_KEY_REPO_FORMAT_VERSION = "repositoryformatversion";

	/** The "worktree" key */
	public static final String CONFIG_KEY_WORKTREE = "worktree";

	/** The "remote" key */
	public static final String CONFIG_KEY_REMOTE = "remote";

	/** The "merge" key */
	public static final String CONFIG_KEY_MERGE = "merge";

	/** The "rebase" key */
	public static final String CONFIG_KEY_REBASE = "rebase";

	/** The "url" key */
	public static final String CONFIG_KEY_URL = "url";

	/** The "autosetupmerge" key */
	public static final String CONFIG_KEY_AUTOSETUPMERGE = "autosetupmerge";

	/** The "autosetuprebase" key */
	public static final String CONFIG_KEY_AUTOSETUPREBASE = "autosetuprebase";
	/** The "name" key */
	public static final String CONFIG_KEY_NAME = "name";

	/** The "email" key */
	public static final String CONFIG_KEY_EMAIL = "email";

	/** The "false" key (used to configure {@link #CONFIG_KEY_AUTOSETUPMERGE} */
	public static final String CONFIG_KEY_FALSE = "false";

	/** The "true" key (used to configure {@link #CONFIG_KEY_AUTOSETUPMERGE} */
	public static final String CONFIG_KEY_TRUE = "true";

	/**
	 * The "always" key (used to configure {@link #CONFIG_KEY_AUTOSETUPREBASE}
	 * and {@link #CONFIG_KEY_AUTOSETUPMERGE}
	 */
	public static final String CONFIG_KEY_ALWAYS = "always";

	/** The "never" key (used to configure {@link #CONFIG_KEY_AUTOSETUPREBASE} */
	public static final String CONFIG_KEY_NEVER = "never";

	/** The "local" key (used to configure {@link #CONFIG_KEY_AUTOSETUPREBASE} */
	public static final String CONFIG_KEY_LOCAL = "local";

	/** The "createchangeid" key */
	public static final String CONFIG_KEY_CREATECHANGEID = "createchangeid";
}
