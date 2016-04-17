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

import static org.eclipse.jgit.internal.ketch.KetchReplica.CommitMethod.ALL_REFS;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NODELETE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;
import static org.eclipse.jgit.lib.Ref.Storage.NETWORK;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

/**
 * Representation of a Git repository on a remote replica system.
 * <p>
 * {@link KetchLeader} will contact the replica using the Git wire protocol.
 * <p>
 * The remote replica may be fully Ketch-aware, or a standard Git server.
 */
public class RemoteGitReplica extends KetchReplica {
	private final URIish uri;
	private final RemoteConfig remoteConfig;

	/**
	 * Configure a new remote.
	 *
	 * @param leader
	 *            instance this replica follows.
	 * @param name
	 *            unique-ish name identifying this remote for debugging.
	 * @param uri
	 *            URI to connect to the follower's repository.
	 * @param cfg
	 *            how Ketch should treat the remote system.
	 * @param rc
	 *            optional remote configuration describing how to contact the
	 *            peer repository.
	 */
	public RemoteGitReplica(KetchLeader leader, String name, URIish uri,
			ReplicaConfig cfg, @Nullable RemoteConfig rc) {
		super(leader, name, cfg);
		this.uri = uri;
		this.remoteConfig = rc;
	}

	/** @return URI to contact the remote peer repository. */
	public URIish getURI() {
		return uri;
	}

	/** @return optional configuration describing how to contact the peer. */
	@Nullable
	protected RemoteConfig getRemoteConfig() {
		return remoteConfig;
	}

	@Override
	protected String describeForLog() {
		return String.format("%s @ %s", getName(), getURI()); //$NON-NLS-1$
	}

	@Override
	protected void startPush(final ReplicaPushRequest req) {
		getSystem().getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				try (Repository git = getLeader().openRepository()) {
					try {
						push(git, req);
						req.done(git);
					} catch (Throwable err) {
						req.setException(git, err);
					}
				} catch (IOException err) {
					req.setException(null, err);
				}
			}
		});
	}

	private void push(Repository repo, ReplicaPushRequest req)
			throws NotSupportedException, TransportException, IOException {
		Map<String, Ref> adv;
		List<RemoteCommand> cmds = asUpdateList(req.getCommands());
		try (Transport transport = Transport.open(repo, uri)) {
			RemoteConfig rc = getRemoteConfig();
			if (rc != null) {
				transport.applyConfig(rc);
			}
			transport.setPushAtomic(true);
			adv = push(repo, transport, cmds);
		}
		for (RemoteCommand c : cmds) {
			c.copyStatusToResult();
		}
		req.setRefs(adv);
	}

	private Map<String, Ref> push(Repository git, Transport transport,
			List<RemoteCommand> cmds) throws IOException {
		Map<String, RemoteRefUpdate> updates = asUpdateMap(cmds);
		try (PushConnection connection = transport.openPush()) {
			Map<String, Ref> adv = connection.getRefsMap();
			RemoteRefUpdate accepted = updates.get(getSystem().getTxnAccepted());
			if (accepted != null && !isExpectedValue(adv, accepted)) {
				abort(cmds);
				return adv;
			}

			RemoteRefUpdate committed = updates.get(getSystem().getTxnCommitted());
			if (committed != null && !isExpectedValue(adv, committed)) {
				abort(cmds);
				return adv;
			}
			if (committed != null && getCommitMethod() == ALL_REFS) {
				prepareCommit(git, cmds, updates, adv,
						committed.getNewObjectId());
			}

			connection.push(NullProgressMonitor.INSTANCE, updates);
			return adv;
		}
	}

	private static boolean isExpectedValue(Map<String, Ref> adv,
			RemoteRefUpdate u) {
		Ref r = adv.get(u.getRemoteName());
		if (!AnyObjectId.equals(getId(r), u.getExpectedOldObjectId())) {
			((RemoteCommand) u).cmd.setResult(LOCK_FAILURE);
			return false;
		}
		return true;
	}

	private void prepareCommit(Repository git, List<RemoteCommand> cmds,
			Map<String, RemoteRefUpdate> updates, Map<String, Ref> adv,
			ObjectId committed) throws IOException {
		for (ReceiveCommand cmd : prepareCommit(git, adv, committed)) {
			RemoteCommand c = new RemoteCommand(cmd);
			cmds.add(c);
			updates.put(c.getRemoteName(), c);
		}
	}

	private static List<RemoteCommand> asUpdateList(
			Collection<ReceiveCommand> cmds) {
		try {
			List<RemoteCommand> toPush = new ArrayList<>(cmds.size());
			for (ReceiveCommand cmd : cmds) {
				toPush.add(new RemoteCommand(cmd));
			}
			return toPush;
		} catch (IOException e) {
			// Cannot occur as no IO was required to build the command.
			throw new IllegalStateException(e);
		}
	}

	private static Map<String, RemoteRefUpdate> asUpdateMap(
			List<RemoteCommand> cmds) {
		Map<String, RemoteRefUpdate> m = new LinkedHashMap<>();
		for (RemoteCommand cmd : cmds) {
			m.put(cmd.getRemoteName(), cmd);
		}
		return m;
	}

	private static void abort(List<RemoteCommand> cmds) {
		List<ReceiveCommand> tmp = new ArrayList<>(cmds.size());
		for (RemoteCommand cmd : cmds) {
			tmp.add(cmd.cmd);
		}
		ReceiveCommand.abort(tmp);
	}

	protected void blockingFetch(Repository repo, ReplicaFetchRequest req)
			throws NotSupportedException, TransportException {
		try (Transport transport = Transport.open(repo, uri)) {
			RemoteConfig rc = getRemoteConfig();
			if (rc != null) {
				transport.applyConfig(rc);
			}
			fetch(transport, req);
		}
	}

	private void fetch(Transport transport, ReplicaFetchRequest req)
			throws NotSupportedException, TransportException {
		try (FetchConnection conn = transport.openFetch()) {
			Map<String, Ref> remoteRefs = conn.getRefsMap();
			req.setRefs(remoteRefs);

			List<Ref> want = new ArrayList<>();
			for (String name : req.getWantRefs()) {
				Ref ref = remoteRefs.get(name);
				if (ref != null && ref.getObjectId() != null) {
					want.add(ref);
				}
			}
			for (ObjectId id : req.getWantObjects()) {
				want.add(new ObjectIdRef.Unpeeled(NETWORK, id.name(), id));
			}

			conn.fetch(NullProgressMonitor.INSTANCE, want,
					Collections.<ObjectId> emptySet());
		}
	}

	static class RemoteCommand extends RemoteRefUpdate {
		final ReceiveCommand cmd;

		RemoteCommand(ReceiveCommand cmd) throws IOException {
			super(null, null,
					cmd.getNewId(), cmd.getRefName(),
					true /* force update */,
					null /* no local tracking ref */,
					cmd.getOldId());
			this.cmd = cmd;
		}

		void copyStatusToResult() {
			if (cmd.getResult() == NOT_ATTEMPTED) {
				switch (getStatus()) {
				case OK:
				case UP_TO_DATE:
				case NON_EXISTING:
					cmd.setResult(OK);
					break;

				case REJECTED_NODELETE:
					cmd.setResult(REJECTED_NODELETE);
					break;

				case REJECTED_NONFASTFORWARD:
					cmd.setResult(REJECTED_NONFASTFORWARD);
					break;

				case REJECTED_OTHER_REASON:
					cmd.setResult(REJECTED_OTHER_REASON, getMessage());
					break;

				default:
					cmd.setResult(REJECTED_OTHER_REASON, getStatus().name());
					break;
				}
			}
		}
	}
}
