/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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

package org.eclipse.jgit.attributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.attributes.Attribute.State;

/**
 * Represents a set of attributes for a path
 * <p>
 *
 * @since 4.2
 */
public final class Attributes {
	private final Map<String, Attribute> map = new LinkedHashMap<>();

	/**
	 * Creates a new instance
	 *
	 * @param attributes
	 */
	public Attributes(Attribute... attributes) {
		if (attributes != null) {
			for (Attribute a : attributes) {
				put(a);
			}
		}
	}

	/**
	 * @return true if the set does not contain any attributes
	 */
	public boolean isEmpty() {
		return map.isEmpty();
	}

	/**
	 * @param key
	 * @return the attribute or null
	 */
	public Attribute get(String key) {
		return map.get(key);
	}

	/**
	 * @return all attributes
	 */
	public Collection<Attribute> getAll() {
		return new ArrayList<>(map.values());
	}

	/**
	 * @param a
	 */
	public void put(Attribute a) {
		map.put(a.getKey(), a);
	}

	/**
	 * @param key
	 */
	public void remove(String key) {
		map.remove(key);
	}

	/**
	 * @param key
	 * @return true if the {@link Attributes} contains this key
	 */
	public boolean containsKey(String key) {
		return map.containsKey(key);
	}

	/**
	 * Returns the state.
	 *
	 * @param key
	 *
	 * @return the state (never returns <code>null</code>)
	 */
	public Attribute.State getState(String key) {
		Attribute a = map.get(key);
		return a != null ? a.getState() : Attribute.State.UNSPECIFIED;
	}

	/**
	 * @param key
	 * @return true if the key is {@link State#SET}, false in all other cases
	 */
	public boolean isSet(String key) {
		return (getState(key) == State.SET);
	}

	/**
	 * @param key
	 * @return true if the key is {@link State#UNSET}, false in all other cases
	 */
	public boolean isUnset(String key) {
		return (getState(key) == State.UNSET);
	}

	/**
	 * @param key
	 * @return true if the key is {@link State#UNSPECIFIED}, false in all other
	 *         cases
	 */
	public boolean isUnspecified(String key) {
		return (getState(key) == State.UNSPECIFIED);
	}

	/**
	 * @param key
	 * @return true if the key is {@link State#CUSTOM}, false in all other cases
	 *         see {@link #getValue(String)} for the value of the key
	 */
	public boolean isCustom(String key) {
		return (getState(key) == State.CUSTOM);
	}

	/**
	 * @param key
	 * @return the attribute value (may be <code>null</code>)
	 */
	public String getValue(String key) {
		Attribute a = map.get(key);
		return a != null ? a.getValue() : null;
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(getClass().getSimpleName());
		buf.append("["); //$NON-NLS-1$
		buf.append(" "); //$NON-NLS-1$
		for (Attribute a : map.values()) {
			buf.append(a.toString());
			buf.append(" "); //$NON-NLS-1$
		}
		buf.append("]"); //$NON-NLS-1$
		return buf.toString();
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Attributes))
			return false;
		Attributes other = (Attributes) obj;
		return this.map.equals(other.map);
	}

}
