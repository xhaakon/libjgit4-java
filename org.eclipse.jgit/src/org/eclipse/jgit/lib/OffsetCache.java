/*
 * Copyright (C) 2009, Google Inc.
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

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Least frequently used cache for objects specified by PackFile positions.
 * <p>
 * This cache maps a <code>({@link PackFile},position)</code> tuple to an Object.
 * <p>
 * This cache is suitable for objects that are "relative expensive" to compute
 * from the underlying PackFile, given some known position in that file.
 * <p>
 * Whenever a cache miss occurs, {@link #load(PackFile, long)} is invoked by
 * exactly one thread for the given <code>(PackFile,position)</code> key tuple.
 * This is ensured by an array of locks, with the tuple hashed to a lock
 * instance.
 * <p>
 * During a miss, older entries are evicted from the cache so long as
 * {@link #isFull()} returns true.
 * <p>
 * Its too expensive during object access to be 100% accurate with a least
 * recently used (LRU) algorithm. Strictly ordering every read is a lot of
 * overhead that typically doesn't yield a corresponding benefit to the
 * application.
 * <p>
 * This cache implements a loose LRU policy by randomly picking a window
 * comprised of roughly 10% of the cache, and evicting the oldest accessed entry
 * within that window.
 * <p>
 * Entities created by the cache are held under SoftReferences, permitting the
 * Java runtime's garbage collector to evict entries when heap memory gets low.
 * Most JREs implement a loose least recently used algorithm for this eviction.
 * <p>
 * The internal hash table does not expand at runtime, instead it is fixed in
 * size at cache creation time. The internal lock table used to gate load
 * invocations is also fixed in size.
 * <p>
 * The key tuple is passed through to methods as a pair of parameters rather
 * than as a single Object, thus reducing the transient memory allocations of
 * callers. It is more efficient to avoid the allocation, as we can't be 100%
 * sure that a JIT would be able to stack-allocate a key tuple.
 * <p>
 * This cache has an implementation rule such that:
 * <ul>
 * <li>{@link #load(PackFile, long)} is invoked by at most one thread at a time
 * for a given <code>(PackFile,position)</code> tuple.</li>
 * <li>For every <code>load()</code> invocation there is exactly one
 * {@link #createRef(PackFile, long, Object)} invocation to wrap a SoftReference
 * around the cached entity.</li>
 * <li>For every Reference created by <code>createRef()</code> there will be
 * exactly one call to {@link #clear(Ref)} to cleanup any resources associated
 * with the (now expired) cached entity.</li>
 * </ul>
 * <p>
 * Therefore, it is safe to perform resource accounting increments during the
 * {@link #load(PackFile, long)} or {@link #createRef(PackFile, long, Object)}
 * methods, and matching decrements during {@link #clear(Ref)}. Implementors may
 * need to override {@link #createRef(PackFile, long, Object)} in order to embed
 * additional accounting information into an implementation specific
 * {@link OffsetCache.Ref} subclass, as the cached entity may have already been
 * evicted by the JRE's garbage collector.
 * <p>
 * To maintain higher concurrency workloads, during eviction only one thread
 * performs the eviction work, while other threads can continue to insert new
 * objects in parallel. This means that the cache can be temporarily over limit,
 * especially if the nominated eviction thread is being starved relative to the
 * other threads.
 *
 * @param <V>
 *            type of value stored in the cache.
 * @param <R>
 *            type of {@link OffsetCache.Ref} subclass used by the cache.
 */
abstract class OffsetCache<V, R extends OffsetCache.Ref<V>> {
	private static final Random rng = new Random();

	/** ReferenceQueue that {@link #createRef(PackFile, long, Object)} must use. */
	protected final ReferenceQueue<V> queue;

	/** Number of entries in {@link #table}. */
	private final int tableSize;

	/** Access clock for loose LRU. */
	private final AtomicLong clock;

	/** Hash bucket directory; entries are chained below. */
	private final AtomicReferenceArray<Entry<V>> table;

	/** Locks to prevent concurrent loads for same (PackFile,position). */
	private final Lock[] locks;

	/** Lock to elect the eviction thread after a load occurs. */
	private final ReentrantLock evictLock;

	/** Number of {@link #table} buckets to scan for an eviction window. */
	private final int evictBatch;

	/**
	 * Create a new cache with a fixed size entry table and lock table.
	 *
	 * @param tSize
	 *            number of entries in the entry hash table.
	 * @param lockCount
	 *            number of entries in the lock table. This is the maximum
	 *            concurrency rate for creation of new objects through
	 *            {@link #load(PackFile, long)} invocations.
	 */
	OffsetCache(final int tSize, final int lockCount) {
		if (tSize < 1)
			throw new IllegalArgumentException("tSize must be >= 1");
		if (lockCount < 1)
			throw new IllegalArgumentException("lockCount must be >= 1");

		queue = new ReferenceQueue<V>();
		tableSize = tSize;
		clock = new AtomicLong(1);
		table = new AtomicReferenceArray<Entry<V>>(tableSize);
		locks = new Lock[lockCount];
		for (int i = 0; i < locks.length; i++)
			locks[i] = new Lock();
		evictLock = new ReentrantLock();

		int eb = (int) (tableSize * .1);
		if (64 < eb)
			eb = 64;
		else if (eb < 4)
			eb = 4;
		if (tableSize < eb)
			eb = tableSize;
		evictBatch = eb;
	}

	/**
	 * Lookup a cached object, creating and loading it if it doesn't exist.
	 *
	 * @param pack
	 *            the pack that "contains" the cached object.
	 * @param position
	 *            offset within <code>pack</code> of the object.
	 * @return the object reference.
	 * @throws IOException
	 *             the object reference was not in the cache and could not be
	 *             obtained by {@link #load(PackFile, long)}.
	 */
	V getOrLoad(final PackFile pack, final long position) throws IOException {
		final int slot = slot(pack, position);
		final Entry<V> e1 = table.get(slot);
		V v = scan(e1, pack, position);
		if (v != null)
			return v;

		synchronized (lock(pack, position)) {
			Entry<V> e2 = table.get(slot);
			if (e2 != e1) {
				v = scan(e2, pack, position);
				if (v != null)
					return v;
			}

			v = load(pack, position);
			final Ref<V> ref = createRef(pack, position, v);
			hit(ref);
			for (;;) {
				final Entry<V> n = new Entry<V>(clean(e2), ref);
				if (table.compareAndSet(slot, e2, n))
					break;
				e2 = table.get(slot);
			}
		}

		if (evictLock.tryLock()) {
			try {
				gc();
				evict();
			} finally {
				evictLock.unlock();
			}
		}

		return v;
	}

	private V scan(Entry<V> n, final PackFile pack, final long position) {
		for (; n != null; n = n.next) {
			final Ref<V> r = n.ref;
			if (r.pack == pack && r.position == position) {
				final V v = r.get();
				if (v != null) {
					hit(r);
					return v;
				}
				n.kill();
				break;
			}
		}
		return null;
	}

	private void hit(final Ref<V> r) {
		// We don't need to be 100% accurate here. Its sufficient that at least
		// one thread performs the increment. Any other concurrent access at
		// exactly the same time can simply use the same clock value.
		//
		// Consequently we attempt the set, but we don't try to recover should
		// it fail. This is why we don't use getAndIncrement() here.
		//
		final long c = clock.get();
		clock.compareAndSet(c, c + 1);
		r.lastAccess = c;
	}

	private void evict() {
		while (isFull()) {
			int ptr = rng.nextInt(tableSize);
			Entry<V> old = null;
			int slot = 0;
			for (int b = evictBatch - 1; b >= 0; b--, ptr++) {
				if (tableSize <= ptr)
					ptr = 0;
				for (Entry<V> e = table.get(ptr); e != null; e = e.next) {
					if (e.dead)
						continue;
					if (old == null || e.ref.lastAccess < old.ref.lastAccess) {
						old = e;
						slot = ptr;
					}
				}
			}
			if (old != null) {
				old.kill();
				gc();
				final Entry<V> e1 = table.get(slot);
				table.compareAndSet(slot, e1, clean(e1));
			}
		}
	}

	/**
	 * Clear every entry from the cache.
	 *<p>
	 * This is a last-ditch effort to clear out the cache, such as before it
	 * gets replaced by another cache that is configured differently. This
	 * method tries to force every cached entry through {@link #clear(Ref)} to
	 * ensure that resources are correctly accounted for and cleaned up by the
	 * subclass. A concurrent reader loading entries while this method is
	 * running may cause resource accounting failures.
	 */
	void removeAll() {
		for (int s = 0; s < tableSize; s++) {
			Entry<V> e1;
			do {
				e1 = table.get(s);
				for (Entry<V> e = e1; e != null; e = e.next)
					e.kill();
			} while (!table.compareAndSet(s, e1, null));
		}
		gc();
	}

	/**
	 * Clear all entries related to a single file.
	 * <p>
	 * Typically this method is invoked during {@link PackFile#close()}, when we
	 * know the pack is never going to be useful to us again (for example, it no
	 * longer exists on disk). A concurrent reader loading an entry from this
	 * same pack may cause the pack to become stuck in the cache anyway.
	 *
	 * @param pack
	 *            the file to purge all entries of.
	 */
	void removeAll(final PackFile pack) {
		for (int s = 0; s < tableSize; s++) {
			final Entry<V> e1 = table.get(s);
			boolean hasDead = false;
			for (Entry<V> e = e1; e != null; e = e.next) {
				if (e.ref.pack == pack) {
					e.kill();
					hasDead = true;
				} else if (e.dead)
					hasDead = true;
			}
			if (hasDead)
				table.compareAndSet(s, e1, clean(e1));
		}
		gc();
	}

	/**
	 * Materialize an object that doesn't yet exist in the cache.
	 * <p>
	 * This method is invoked by {@link #getOrLoad(PackFile, long)} when the
	 * specified entity does not yet exist in the cache. Internal locking
	 * ensures that at most one thread can call this method for each unique
	 * <code>(pack,position)</code>, but multiple threads can call this method
	 * concurrently for different <code>(pack,position)</code> tuples.
	 *
	 * @param pack
	 *            the file to materialize the entry from.
	 * @param position
	 *            offset within the file of the entry.
	 * @return the materialized object. Must never be null.
	 * @throws IOException
	 *             the method was unable to materialize the object for this
	 *             input pair. The usual reasons would be file corruption, file
	 *             not found, out of file descriptors, etc.
	 */
	protected abstract V load(PackFile pack, long position) throws IOException;

	/**
	 * Construct a Ref (SoftReference) around a cached entity.
	 * <p>
	 * Implementing this is only necessary if the subclass is performing
	 * resource accounting during {@link #load(PackFile, long)} and
	 * {@link #clear(Ref)} requires some information to update the accounting.
	 * <p>
	 * Implementors <b>MUST</b> ensure that the returned reference uses the
	 * {@link #queue} ReferenceQueue, otherwise {@link #clear(Ref)} will not be
	 * invoked at the proper time.
	 *
	 * @param pack
	 *            the file to materialize the entry from.
	 * @param position
	 *            offset within the file of the entry.
	 * @param v
	 *            the object returned by {@link #load(PackFile, long)}.
	 * @return a soft reference subclass wrapped around <code>v</code>.
	 */
	@SuppressWarnings("unchecked")
	protected R createRef(final PackFile pack, final long position, final V v) {
		return (R) new Ref<V>(pack, position, v, queue);
	}

	/**
	 * Update accounting information now that an object has left the cache.
	 * <p>
	 * This method is invoked exactly once for the combined
	 * {@link #load(PackFile, long)} and
	 * {@link #createRef(PackFile, long, Object)} invocation pair that was used
	 * to construct and insert an object into the cache.
	 *
	 * @param ref
	 *            the reference wrapped around the object. Implementations must
	 *            be prepared for <code>ref.get()</code> to return null.
	 */
	protected void clear(final R ref) {
		// Do nothing by default.
	}

	/**
	 * Determine if the cache is full and requires eviction of entries.
	 * <p>
	 * By default this method returns false. Implementors may override to
	 * consult with the accounting updated by {@link #load(PackFile, long)},
	 * {@link #createRef(PackFile, long, Object)} and {@link #clear(Ref)}.
	 *
	 * @return true if the cache is still over-limit and requires eviction of
	 *         more entries.
	 */
	protected boolean isFull() {
		return false;
	}

	@SuppressWarnings("unchecked")
	private void gc() {
		R r;
		while ((r = (R) queue.poll()) != null) {
			// Sun's Java 5 and 6 implementation have a bug where a Reference
			// can be enqueued and dequeued twice on the same reference queue
			// due to a race condition within ReferenceQueue.enqueue(Reference).
			//
			// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6837858
			//
			// We CANNOT permit a Reference to come through us twice, as it will
			// skew the resource counters we maintain. Our canClear() check here
			// provides a way to skip the redundant dequeues, if any.
			//
			if (r.canClear()) {
				clear(r);

				boolean found = false;
				final int s = slot(r.pack, r.position);
				final Entry<V> e1 = table.get(s);
				for (Entry<V> n = e1; n != null; n = n.next) {
					if (n.ref == r) {
						n.dead = true;
						found = true;
						break;
					}
				}
				if (found)
					table.compareAndSet(s, e1, clean(e1));
			}
		}
	}

	/**
	 * Compute the hash code value for a <code>(PackFile,position)</code> tuple.
	 * <p>
	 * For example, <code>return packHash + (int) (position >>> 4)</code>.
	 * Implementors must override with a suitable hash (for example, a different
	 * right shift on the position).
	 *
	 * @param packHash
	 *            hash code for the file being accessed.
	 * @param position
	 *            position within the file being accessed.
	 * @return a reasonable hash code mixing the two values.
	 */
	protected abstract int hash(int packHash, long position);

	private int slot(final PackFile pack, final long position) {
		return (hash(pack.hash, position) >>> 1) % tableSize;
	}

	private Lock lock(final PackFile pack, final long position) {
		return locks[(hash(pack.hash, position) >>> 1) % locks.length];
	}

	private static <V> Entry<V> clean(Entry<V> top) {
		while (top != null && top.dead) {
			top.ref.enqueue();
			top = top.next;
		}
		if (top == null)
			return null;
		final Entry<V> n = clean(top.next);
		return n == top.next ? top : new Entry<V>(n, top.ref);
	}

	private static class Entry<V> {
		/** Next entry in the hash table's chain list. */
		final Entry<V> next;

		/** The referenced object. */
		final Ref<V> ref;

		/**
		 * Marked true when ref.get() returns null and the ref is dead.
		 * <p>
		 * A true here indicates that the ref is no longer accessible, and that
		 * we therefore need to eventually purge this Entry object out of the
		 * bucket's chain.
		 */
		volatile boolean dead;

		Entry(final Entry<V> n, final Ref<V> r) {
			next = n;
			ref = r;
		}

		final void kill() {
			dead = true;
			ref.enqueue();
		}
	}

	/**
	 * A soft reference wrapped around a cached object.
	 *
	 * @param <V>
	 *            type of the cached object.
	 */
	protected static class Ref<V> extends SoftReference<V> {
		final PackFile pack;

		final long position;

		long lastAccess;

		private boolean cleared;

		protected Ref(final PackFile pack, final long position, final V v,
				final ReferenceQueue<V> queue) {
			super(v, queue);
			this.pack = pack;
			this.position = position;
		}

		final synchronized boolean canClear() {
			if (cleared)
				return false;
			cleared = true;
			return true;
		}
	}

	private static final class Lock {
		// Used only for its implicit monitor.
	}
}
