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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.internal.ketch.KetchConstants.CONFIG_KEY_COMMIT;
import static org.eclipse.jgit.internal.ketch.KetchConstants.CONFIG_KEY_SPEED;
import static org.eclipse.jgit.internal.ketch.KetchConstants.CONFIG_KEY_TYPE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_REMOTE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.ketch.KetchReplica.CommitMethod;
import org.eclipse.jgit.internal.ketch.KetchReplica.CommitSpeed;
import org.eclipse.jgit.internal.ketch.KetchReplica.Participation;
import org.eclipse.jgit.lib.Config;

/** Configures a {@link KetchReplica}. */
public class ReplicaConfig {
	/**
	 * Read a configuration from a config block.
	 *
	 * @param cfg
	 *            configuration to read.
	 * @param name
	 *            of the replica being configured.
	 * @return replica configuration for {@code name}.
	 */
	public static ReplicaConfig newFromConfig(Config cfg, String name) {
		return new ReplicaConfig().fromConfig(cfg, name);
	}

	private Participation participation = Participation.FULL;
	private CommitMethod commitMethod = CommitMethod.ALL_REFS;
	private CommitSpeed commitSpeed = CommitSpeed.BATCHED;
	private long minRetry = SECONDS.toMillis(5);
	private long maxRetry = MINUTES.toMillis(1);

	/** @return participation of the replica in the system. */
	public Participation getParticipation() {
		return participation;
	}

	/** @return how Ketch should apply committed changes. */
	public CommitMethod getCommitMethod() {
		return commitMethod;
	}

	/** @return how quickly should Ketch commit. */
	public CommitSpeed getCommitSpeed() {
		return commitSpeed;
	}

	/**
	 * Returns the minimum wait delay before retrying a failure.
	 *
	 * @param unit
	 *            to get retry delay in.
	 * @return minimum delay before retrying a failure.
	 */
	public long getMinRetry(TimeUnit unit) {
		return unit.convert(minRetry, MILLISECONDS);
	}

	/**
	 * Returns the maximum wait delay before retrying a failure.
	 *
	 * @param unit
	 *            to get retry delay in.
	 * @return maximum delay before retrying a failure.
	 */
	public long getMaxRetry(TimeUnit unit) {
		return unit.convert(maxRetry, MILLISECONDS);
	}

	/**
	 * Update the configuration from a config block.
	 *
	 * @param cfg
	 *            configuration to read.
	 * @param name
	 *            of the replica being configured.
	 * @return {@code this}
	 */
	public ReplicaConfig fromConfig(Config cfg, String name) {
		participation = cfg.getEnum(
				CONFIG_KEY_REMOTE, name, CONFIG_KEY_TYPE,
				participation);
		commitMethod = cfg.getEnum(
				CONFIG_KEY_REMOTE, name, CONFIG_KEY_COMMIT,
				commitMethod);
		commitSpeed = cfg.getEnum(
				CONFIG_KEY_REMOTE, name, CONFIG_KEY_SPEED,
				commitSpeed);
		minRetry = getMillis(cfg, name, "ketch-minRetry", minRetry); //$NON-NLS-1$
		maxRetry = getMillis(cfg, name, "ketch-maxRetry", maxRetry); //$NON-NLS-1$
		return this;
	}

	private static long getMillis(Config cfg, String name, String key,
			long defaultValue) {
		String valStr = cfg.getString(CONFIG_KEY_REMOTE, name, key);
		if (valStr == null) {
			return defaultValue;
		}

		valStr = valStr.trim();
		if (valStr.isEmpty()) {
			return defaultValue;
		}

		Matcher m = UnitMap.PATTERN.matcher(valStr);
		if (!m.matches()) {
			return defaultValue;
		}

		String digits = m.group(1);
		String unitName = m.group(2).trim();
		TimeUnit unit = UnitMap.UNITS.get(unitName);
		if (unit == null) {
			return defaultValue;
		}

		try {
			if (digits.indexOf('.') == -1) {
				return unit.toMillis(Long.parseLong(digits));
			}

			double val = Double.parseDouble(digits);
			return (long) (val * unit.toMillis(1));
		} catch (NumberFormatException nfe) {
			return defaultValue;
		}
	}

	static class UnitMap {
		static final Pattern PATTERN = Pattern
				.compile("^([1-9][0-9]*(?:\\.[0-9]*)?)\\s*(.*)$"); //$NON-NLS-1$

		static final Map<String, TimeUnit> UNITS;

		static {
			Map<String, TimeUnit> m = new HashMap<>();
			TimeUnit u = MILLISECONDS;
			m.put("", u); //$NON-NLS-1$
			m.put("ms", u); //$NON-NLS-1$
			m.put("millis", u); //$NON-NLS-1$
			m.put("millisecond", u); //$NON-NLS-1$
			m.put("milliseconds", u); //$NON-NLS-1$

			u = SECONDS;
			m.put("s", u); //$NON-NLS-1$
			m.put("sec", u); //$NON-NLS-1$
			m.put("secs", u); //$NON-NLS-1$
			m.put("second", u); //$NON-NLS-1$
			m.put("seconds", u); //$NON-NLS-1$

			u = MINUTES;
			m.put("m", u); //$NON-NLS-1$
			m.put("min", u); //$NON-NLS-1$
			m.put("mins", u); //$NON-NLS-1$
			m.put("minute", u); //$NON-NLS-1$
			m.put("minutes", u); //$NON-NLS-1$

			u = HOURS;
			m.put("h", u); //$NON-NLS-1$
			m.put("hr", u); //$NON-NLS-1$
			m.put("hrs", u); //$NON-NLS-1$
			m.put("hour", u); //$NON-NLS-1$
			m.put("hours", u); //$NON-NLS-1$

			u = DAYS;
			m.put("d", u); //$NON-NLS-1$
			m.put("day", u); //$NON-NLS-1$
			m.put("days", u); //$NON-NLS-1$

			UNITS = Collections.unmodifiableMap(m);
		}

		private UnitMap() {
		}
	}
}
