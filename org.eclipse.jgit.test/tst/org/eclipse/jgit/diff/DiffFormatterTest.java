 * Copyright (C) 2010, 2013 Google Inc.
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
	private TestRepository<Repository> testDb;
		testDb = new TestRepository<Repository>(db);
	@Test
	public void testCreateFileHeaderWithoutIndexLine() throws Exception {
		DiffEntry m = DiffEntry.modify(PATH_A);
		m.oldMode = FileMode.REGULAR_FILE;
		m.newMode = FileMode.EXECUTABLE_FILE;

		FileHeader fh = df.toFileHeader(m);
		String expected = DIFF + "a/src/a b/src/a\n" + //
				"old mode 100644\n" + //
				"new mode 100755\n";
		assertEquals(expected, fh.getScriptText());
	}

	@Test
	public void testCreateFileHeaderForRenameWithoutContentChange() throws Exception {
		DiffEntry a = DiffEntry.delete(PATH_A, ObjectId.zeroId());
		DiffEntry b = DiffEntry.add(PATH_B, ObjectId.zeroId());
		DiffEntry m = DiffEntry.pair(ChangeType.RENAME, a, b, 100);
		m.oldId = null;
		m.newId = null;

		FileHeader fh = df.toFileHeader(m);
		String expected = DIFF + "a/src/a b/src/b\n" + //
				"similarity index 100%\n" + //
				"rename from src/a\n" + //
				"rename to src/b\n";
		assertEquals(expected, fh.getScriptText());
	}

	private static String makeDiffHeader(String pathA, String pathB,
			ObjectId aId,
	private static String makeDiffHeaderModeChange(String pathA, String pathB,