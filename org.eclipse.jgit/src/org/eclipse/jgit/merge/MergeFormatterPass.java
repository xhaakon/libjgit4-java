/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2014, André de Oliveira <andre.oliveira@liferay.com>
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

package org.eclipse.jgit.merge;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.merge.MergeChunk.ConflictState;

class MergeFormatterPass {

	private final EolAwareOutputStream out;

	private final MergeResult<RawText> res;

	private final List<String> seqName;

	private final String charsetName;

	private final boolean threeWayMerge;

	private String lastConflictingName; // is set to non-null whenever we are in
										// a conflict

	MergeFormatterPass(OutputStream out, MergeResult<RawText> res, List<String> seqName,
			String charsetName) {
		this.out = new EolAwareOutputStream(out);
		this.res = res;
		this.seqName = seqName;
		this.charsetName = charsetName;
		this.threeWayMerge = (res.getSequences().size() == 3);
	}

	void formatMerge() throws IOException {
		boolean missingNewlineAtEnd = false;
		for (MergeChunk chunk : res) {
			RawText seq = res.getSequences().get(chunk.getSequenceIndex());
			writeConflictMetadata(chunk);
			// the lines with conflict-metadata are written. Now write the chunk
			for (int i = chunk.getBegin(); i < chunk.getEnd(); i++)
				writeLine(seq, i);
			missingNewlineAtEnd = seq.isMissingNewlineAtEnd();
		}
		// one possible leftover: if the merge result ended with a conflict we
		// have to close the last conflict here
		if (lastConflictingName != null)
			writeConflictEnd();
		if (!missingNewlineAtEnd)
			out.beginln();
	}

	private void writeConflictMetadata(MergeChunk chunk) throws IOException {
		if (lastConflictingName != null
				&& chunk.getConflictState() != ConflictState.NEXT_CONFLICTING_RANGE) {
			// found the end of an conflict
			writeConflictEnd();
		}
		if (chunk.getConflictState() == ConflictState.FIRST_CONFLICTING_RANGE) {
			// found the start of an conflict
			writeConflictStart(chunk);
		} else if (chunk.getConflictState() == ConflictState.NEXT_CONFLICTING_RANGE) {
			// found another conflicting chunk
			writeConflictChange(chunk);
		}
	}

	private void writeConflictEnd() throws IOException {
		writeln(">>>>>>> " + lastConflictingName); //$NON-NLS-1$
		lastConflictingName = null;
	}

	private void writeConflictStart(MergeChunk chunk) throws IOException {
		lastConflictingName = seqName.get(chunk.getSequenceIndex());
		writeln("<<<<<<< " + lastConflictingName); //$NON-NLS-1$
	}

	private void writeConflictChange(MergeChunk chunk) throws IOException {
		/*
		 * In case of a non-three-way merge I'll add the name of the conflicting
		 * chunk behind the equal signs. I also append the name of the last
		 * conflicting chunk after the ending greater-than signs. If somebody
		 * knows a better notation to present non-three-way merges - feel free
		 * to correct here.
		 */
		lastConflictingName = seqName.get(chunk.getSequenceIndex());
		writeln(threeWayMerge ? "=======" : "======= " //$NON-NLS-1$ //$NON-NLS-2$
				+ lastConflictingName);
	}

	private void writeln(String s) throws IOException {
		out.beginln();
		out.write((s + "\n").getBytes(charsetName)); //$NON-NLS-1$
	}

	private void writeLine(RawText seq, int i) throws IOException {
		out.beginln();
		seq.writeLine(out, i);
		// still BOL? It was a blank line. But writeLine won't lf, so we do.
		if (out.isBeginln())
			out.write('\n');
	}
}