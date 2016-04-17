/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.internal.ketch;

import static org.eclipse.jgit.internal.ketch.KetchConstants.TERM;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The initial {@link Round} for a leaderless repository, used to establish a
 * leader.
 */
class ElectionRound extends Round {
	private static final Logger log = LoggerFactory.getLogger(ElectionRound.class);

	private long term;

	ElectionRound(KetchLeader leader, LogIndex head) {
		super(leader, head);
	}

	@Override
	void start() throws IOException {
		ObjectId id;
		try (Repository git = leader.openRepository();
				ObjectInserter inserter = git.newObjectInserter()) {
			id = bumpTerm(git, inserter);
			inserter.flush();
		}
		runAsync(id);
	}

	@Override
	void success() {
		// Do nothing upon election, KetchLeader will copy the term.
	}

	long getTerm() {
		return term;
	}

	private ObjectId bumpTerm(Repository git, ObjectInserter inserter)
			throws IOException {
		CommitBuilder b = new CommitBuilder();
		if (!ObjectId.zeroId().equals(acceptedOldIndex)) {
			try (RevWalk rw = new RevWalk(git)) {
				RevCommit c = rw.parseCommit(acceptedOldIndex);
				b.setTreeId(c.getTree());
				b.setParentId(acceptedOldIndex);
				term = parseTerm(c.getFooterLines(TERM)) + 1;
			}
		} else {
			term = 1;
			b.setTreeId(inserter.insert(new TreeFormatter()));
		}

		StringBuilder msg = new StringBuilder();
		msg.append(KetchConstants.TERM.getName())
				.append(": ") //$NON-NLS-1$
				.append(term);

		String tag = leader.getSystem().newLeaderTag();
		if (tag != null && !tag.isEmpty()) {
			msg.append(' ').append(tag);
		}

		b.setAuthor(leader.getSystem().newCommitter());
		b.setCommitter(b.getAuthor());
		b.setMessage(msg.toString());

		if (log.isDebugEnabled()) {
			log.debug("Trying to elect myself " + b.getMessage()); //$NON-NLS-1$
		}
		return inserter.insert(b);
	}

	private static long parseTerm(List<String> footer) {
		if (footer.isEmpty()) {
			return 0;
		}

		String s = footer.get(0);
		int p = s.indexOf(' ');
		if (p > 0) {
			s = s.substring(0, p);
		}
		return Long.parseLong(s, 10);
	}
}
