/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.pgm.opt;

import java.io.File;
import java.io.IOException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.WindowCursor;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;

/**
 * Custom argument handler {@link AbstractTreeIterator} from string values.
 * <p>
 * Assumes the parser has been initialized with a Repository.
 */
public class AbstractTreeIteratorHandler extends
		OptionHandler<AbstractTreeIterator> {
	private final org.eclipse.jgit.pgm.opt.CmdLineParser clp;

	/**
	 * Create a new handler for the command name.
	 * <p>
	 * This constructor is used only by args4j.
	 *
	 * @param parser
	 * @param option
	 * @param setter
	 */
	public AbstractTreeIteratorHandler(final CmdLineParser parser,
			final OptionDef option,
			final Setter<? super AbstractTreeIterator> setter) {
		super(parser, option, setter);
		clp = (org.eclipse.jgit.pgm.opt.CmdLineParser) parser;
	}

	@Override
	public int parseArguments(final Parameters params) throws CmdLineException {
		final String name = params.getParameter(0);

		if (new File(name).isDirectory()) {
			setter.addValue(new FileTreeIterator(new File(name)));
			return 1;
		}

		if (new File(name).isFile()) {
			final DirCache dirc;
			try {
				dirc = DirCache.read(new File(name));
			} catch (IOException e) {
				throw new CmdLineException(name + " is not an index file", e);
			}
			setter.addValue(new DirCacheIterator(dirc));
			return 1;
		}

		final ObjectId id;
		try {
			id = clp.getRepository().resolve(name);
		} catch (IOException e) {
			throw new CmdLineException(e.getMessage());
		}
		if (id == null)
			throw new CmdLineException(name + " is not a tree");

		final CanonicalTreeParser p = new CanonicalTreeParser();
		final WindowCursor curs = new WindowCursor();
		try {
			p.reset(clp.getRepository(), clp.getRevWalk().parseTree(id), curs);
		} catch (MissingObjectException e) {
			throw new CmdLineException(name + " is not a tree");
		} catch (IncorrectObjectTypeException e) {
			throw new CmdLineException(name + " is not a tree");
		} catch (IOException e) {
			throw new CmdLineException("cannot read " + name + ": "
					+ e.getMessage());
		} finally {
			curs.release();
		}

		setter.addValue(p);
		return 1;
	}

	@Override
	public String getDefaultMetaVariable() {
		return "tree-ish";
	}
}
