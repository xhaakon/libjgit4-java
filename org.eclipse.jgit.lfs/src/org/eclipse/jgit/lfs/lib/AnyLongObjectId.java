/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.lfs.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.NB;

/**
 * A (possibly mutable) SHA-256 abstraction.
 * <p>
 * If this is an instance of {@link MutableLongObjectId} the concept of equality
 * with this instance can alter at any time, if this instance is modified to
 * represent a different object name.
 *
 * Ported to SHA-256 from {@link AnyObjectId}
 *
 * @since 4.3
 */
public abstract class AnyLongObjectId implements Comparable<AnyLongObjectId> {

	/**
	 * Compare two object identifier byte sequences for equality.
	 *
	 * @param firstObjectId
	 *            the first identifier to compare. Must not be null.
	 * @param secondObjectId
	 *            the second identifier to compare. Must not be null.
	 * @return true if the two identifiers are the same.
	 */
	public static boolean equals(final AnyLongObjectId firstObjectId,
			final AnyLongObjectId secondObjectId) {
		if (firstObjectId == secondObjectId)
			return true;

		// We test word 2 first as odds are someone already used our
		// word 1 as a hash code, and applying that came up with these
		// two instances we are comparing for equality. Therefore the
		// first two words are very likely to be identical. We want to
		// break away from collisions as quickly as possible.
		//
		return firstObjectId.w2 == secondObjectId.w2
				&& firstObjectId.w3 == secondObjectId.w3
				&& firstObjectId.w4 == secondObjectId.w4
				&& firstObjectId.w1 == secondObjectId.w1;
	}

	long w1;

	long w2;

	long w3;

	long w4;

	/**
	 * Get the first 8 bits of the LongObjectId.
	 *
	 * This is a faster version of {@code getByte(0)}.
	 *
	 * @return a discriminator usable for a fan-out style map. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 */
	public final int getFirstByte() {
		return (int) (w1 >>> 56);
	}

	/**
	 * Get the second 8 bits of the LongObjectId.
	 *
	 * @return a discriminator usable for a fan-out style map. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 */
	public final int getSecondByte() {
		return (int) ((w1 >>> 48) & 0xff);
	}

	/**
	 * Get any byte from the LongObjectId.
	 *
	 * Callers hard-coding {@code getByte(0)} should instead use the much faster
	 * special case variant {@link #getFirstByte()}.
	 *
	 * @param index
	 *            index of the byte to obtain from the raw form of the
	 *            LongObjectId. Must be in range [0,
	 *            {@link Constants#LONG_OBJECT_ID_LENGTH}).
	 * @return the value of the requested byte at {@code index}. Returned values
	 *         are unsigned and thus are in the range [0,255] rather than the
	 *         signed byte range of [-128, 127].
	 * @throws ArrayIndexOutOfBoundsException
	 *             {@code index} is less than 0, equal to
	 *             {@link Constants#LONG_OBJECT_ID_LENGTH}, or greater than
	 *             {@link Constants#LONG_OBJECT_ID_LENGTH}.
	 */
	public final int getByte(int index) {
		long w;
		switch (index >> 3) {
		case 0:
			w = w1;
			break;
		case 1:
			w = w2;
			break;
		case 2:
			w = w3;
			break;
		case 3:
			w = w4;
			break;
		default:
			throw new ArrayIndexOutOfBoundsException(index);
		}

		return (int) ((w >>> (8 * (15 - (index & 15)))) & 0xff);
	}

	/**
	 * Compare this LongObjectId to another and obtain a sort ordering.
	 *
	 * @param other
	 *            the other id to compare to. Must not be null.
	 * @return &lt; 0 if this id comes before other; 0 if this id is equal to
	 *         other; &gt; 0 if this id comes after other.
	 */
	public final int compareTo(final AnyLongObjectId other) {
		if (this == other)
			return 0;

		int cmp;

		cmp = NB.compareUInt64(w1, other.w1);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, other.w2);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, other.w3);
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, other.w4);
	}

	/**
	 * Compare this LongObjectId to a network-byte-order LongObjectId.
	 *
	 * @param bs
	 *            array containing the other LongObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least
	 *            32 bytes, starting at this position are required.
	 * @return a negative integer, zero, or a positive integer as this object is
	 *         less than, equal to, or greater than the specified object.
	 */
	public final int compareTo(final byte[] bs, final int p) {
		int cmp;

		cmp = NB.compareUInt64(w1, NB.decodeInt64(bs, p));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, NB.decodeInt64(bs, p + 8));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, NB.decodeInt64(bs, p + 16));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, NB.decodeInt64(bs, p + 24));
	}

	/**
	 * Compare this LongObjectId to a network-byte-order LongObjectId.
	 *
	 * @param bs
	 *            array containing the other LongObjectId in network byte order.
	 * @param p
	 *            position within {@code bs} to start the compare at. At least 4
	 *            longs, starting at this position are required.
	 * @return a negative integer, zero, or a positive integer as this object is
	 *         less than, equal to, or greater than the specified object.
	 */
	public final int compareTo(final long[] bs, final int p) {
		int cmp;

		cmp = NB.compareUInt64(w1, bs[p]);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w2, bs[p + 1]);
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt64(w3, bs[p + 2]);
		if (cmp != 0)
			return cmp;

		return NB.compareUInt64(w4, bs[p + 3]);
	}

	/**
	 * Tests if this LongObjectId starts with the given abbreviation.
	 *
	 * @param abbr
	 *            the abbreviation.
	 * @return true if this LongObjectId begins with the abbreviation; else
	 *         false.
	 */
	public boolean startsWith(final AbbreviatedLongObjectId abbr) {
		return abbr.prefixCompare(this) == 0;
	}

	public final int hashCode() {
		return (int) (w1 >> 32);
	}

	/**
	 * Determine if this LongObjectId has exactly the same value as another.
	 *
	 * @param other
	 *            the other id to compare to. May be null.
	 * @return true only if both LongObjectIds have identical bits.
	 */
	public final boolean equals(final AnyLongObjectId other) {
		return other != null ? equals(this, other) : false;
	}

	public final boolean equals(final Object o) {
		if (o instanceof AnyLongObjectId)
			return equals((AnyLongObjectId) o);
		else
			return false;
	}

	/**
	 * Copy this LongObjectId to an output writer in raw binary.
	 *
	 * @param w
	 *            the buffer to copy to. Must be in big endian order.
	 */
	public void copyRawTo(final ByteBuffer w) {
		w.putLong(w1);
		w.putLong(w2);
		w.putLong(w3);
		w.putLong(w4);
	}

	/**
	 * Copy this LongObjectId to a byte array.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyRawTo(final byte[] b, final int o) {
		NB.encodeInt64(b, o, w1);
		NB.encodeInt64(b, o + 8, w2);
		NB.encodeInt64(b, o + 16, w3);
		NB.encodeInt64(b, o + 24, w4);
	}

	/**
	 * Copy this LongObjectId to an long array.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyRawTo(final long[] b, final int o) {
		b[o] = w1;
		b[o + 1] = w2;
		b[o + 2] = w3;
		b[o + 3] = w4;
	}

	/**
	 * Copy this LongObjectId to an output writer in raw binary.
	 *
	 * @param w
	 *            the stream to write to.
	 * @throws IOException
	 *             the stream writing failed.
	 */
	public void copyRawTo(final OutputStream w) throws IOException {
		writeRawLong(w, w1);
		writeRawLong(w, w2);
		writeRawLong(w, w3);
		writeRawLong(w, w4);
	}

	private static void writeRawLong(final OutputStream w, long v)
			throws IOException {
		w.write((int) (v >>> 56));
		w.write((int) (v >>> 48));
		w.write((int) (v >>> 40));
		w.write((int) (v >>> 32));
		w.write((int) (v >>> 24));
		w.write((int) (v >>> 16));
		w.write((int) (v >>> 8));
		w.write((int) v);
	}

	/**
	 * Copy this LongObjectId to an output writer in hex format.
	 *
	 * @param w
	 *            the stream to copy to.
	 * @throws IOException
	 *             the stream writing failed.
	 */
	public void copyTo(final OutputStream w) throws IOException {
		w.write(toHexByteArray());
	}

	/**
	 * Copy this LongObjectId to a byte array in hex format.
	 *
	 * @param b
	 *            the buffer to copy to.
	 * @param o
	 *            the offset within b to write at.
	 */
	public void copyTo(byte[] b, int o) {
		formatHexByte(b, o + 0, w1);
		formatHexByte(b, o + 16, w2);
		formatHexByte(b, o + 32, w3);
		formatHexByte(b, o + 48, w4);
	}

	/**
	 * Copy this LongObjectId to a ByteBuffer in hex format.
	 *
	 * @param b
	 *            the buffer to copy to.
	 */
	public void copyTo(ByteBuffer b) {
		b.put(toHexByteArray());
	}

	private byte[] toHexByteArray() {
		final byte[] dst = new byte[Constants.LONG_OBJECT_ID_STRING_LENGTH];
		formatHexByte(dst, 0, w1);
		formatHexByte(dst, 16, w2);
		formatHexByte(dst, 32, w3);
		formatHexByte(dst, 48, w4);
		return dst;
	}

	private static final byte[] hexbyte = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private static void formatHexByte(final byte[] dst, final int p, long w) {
		int o = p + 15;
		while (o >= p && w != 0) {
			dst[o--] = hexbyte[(int) (w & 0xf)];
			w >>>= 4;
		}
		while (o >= p)
			dst[o--] = '0';
	}

	/**
	 * Copy this LongObjectId to an output writer in hex format.
	 *
	 * @param w
	 *            the stream to copy to.
	 * @throws IOException
	 *             the stream writing failed.
	 */
	public void copyTo(final Writer w) throws IOException {
		w.write(toHexCharArray());
	}

	/**
	 * Copy this LongObjectId to an output writer in hex format.
	 *
	 * @param tmp
	 *            temporary char array to buffer construct into before writing.
	 *            Must be at least large enough to hold 2 digits for each byte
	 *            of object id (64 characters or larger).
	 * @param w
	 *            the stream to copy to.
	 * @throws IOException
	 *             the stream writing failed.
	 */
	public void copyTo(final char[] tmp, final Writer w) throws IOException {
		toHexCharArray(tmp);
		w.write(tmp, 0, Constants.LONG_OBJECT_ID_STRING_LENGTH);
	}

	/**
	 * Copy this LongObjectId to a StringBuilder in hex format.
	 *
	 * @param tmp
	 *            temporary char array to buffer construct into before writing.
	 *            Must be at least large enough to hold 2 digits for each byte
	 *            of object id (64 characters or larger).
	 * @param w
	 *            the string to append onto.
	 */
	public void copyTo(final char[] tmp, final StringBuilder w) {
		toHexCharArray(tmp);
		w.append(tmp, 0, Constants.LONG_OBJECT_ID_STRING_LENGTH);
	}

	char[] toHexCharArray() {
		final char[] dst = new char[Constants.LONG_OBJECT_ID_STRING_LENGTH];
		toHexCharArray(dst);
		return dst;
	}

	private void toHexCharArray(final char[] dst) {
		formatHexChar(dst, 0, w1);
		formatHexChar(dst, 16, w2);
		formatHexChar(dst, 32, w3);
		formatHexChar(dst, 48, w4);
	}

	private static final char[] hexchar = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static void formatHexChar(final char[] dst, final int p, long w) {
		int o = p + 15;
		while (o >= p && w != 0) {
			dst[o--] = hexchar[(int) (w & 0xf)];
			w >>>= 4;
		}
		while (o >= p)
			dst[o--] = '0';
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "AnyLongObjectId[" + name() + "]";
	}

	/**
	 * @return string form of the SHA-256, in lower case hexadecimal.
	 */
	public final String name() {
		return new String(toHexCharArray());
	}

	/**
	 * @return string form of the SHA-256, in lower case hexadecimal.
	 */
	public final String getName() {
		return name();
	}

	/**
	 * Return an abbreviation (prefix) of this object SHA-256.
	 * <p>
	 * This implementation does not guarantee uniqueness. Callers should instead
	 * use {@link ObjectReader#abbreviate(AnyObjectId, int)} to obtain a unique
	 * abbreviation within the scope of a particular object database.
	 *
	 * @param len
	 *            length of the abbreviated string.
	 * @return SHA-256 abbreviation.
	 */
	public AbbreviatedLongObjectId abbreviate(final int len) {
		final long a = AbbreviatedLongObjectId.mask(len, 1, w1);
		final long b = AbbreviatedLongObjectId.mask(len, 2, w2);
		final long c = AbbreviatedLongObjectId.mask(len, 3, w3);
		final long d = AbbreviatedLongObjectId.mask(len, 4, w4);
		return new AbbreviatedLongObjectId(len, a, b, c, d);
	}

	/**
	 * Obtain an immutable copy of this current object.
	 * <p>
	 * Only returns <code>this</code> if this instance is an unsubclassed
	 * instance of {@link LongObjectId}; otherwise a new instance is returned
	 * holding the same value.
	 * <p>
	 * This method is useful to shed any additional memory that may be tied to
	 * the subclass, yet retain the unique identity of the object id for future
	 * lookups within maps and repositories.
	 *
	 * @return an immutable copy, using the smallest memory footprint possible.
	 */
	public final LongObjectId copy() {
		if (getClass() == LongObjectId.class)
			return (LongObjectId) this;
		return new LongObjectId(this);
	}

	/**
	 * Obtain an immutable copy of this current object.
	 * <p>
	 * See {@link #copy()} if <code>this</code> is a possibly subclassed (but
	 * immutable) identity and the application needs a lightweight identity
	 * <i>only</i> reference.
	 *
	 * @return an immutable copy. May be <code>this</code> if this is already an
	 *         immutable instance.
	 */
	public abstract LongObjectId toObjectId();
}
