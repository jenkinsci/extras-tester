package hudson.model;

import java.io.File;
import java.io.IOException;

public class SubversionTestCaseTest extends SubversionTestCase {

	private static final String TEMP_FILE_PREFIX = "svntestcase";

	public void testSvnAdd() throws IOException {
		File tempFile = File.createTempFile(TEMP_FILE_PREFIX, null, svnwc);
		svnAdd(tempFile, "test add");
	}
	
	public void testSvnUpdate() throws IOException {
		File tempFile = File.createTempFile(TEMP_FILE_PREFIX, null, svnwc);
		svnAdd(tempFile, "test add");
		svnCommit(tempFile, "test update");
	}
	
}
