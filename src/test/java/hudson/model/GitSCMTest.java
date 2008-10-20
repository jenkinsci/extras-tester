package hudson.model;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.RemoteRepository;
import hudson.scm.ChangeLogSet;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import org.codehaus.plexus.util.IOUtil;

public class GitSCMTest extends HudsonTestCase {
	File externalRepo;

	@Override
	/**
	 * Initializes the Git repository. Provides
	 * <tt>File externalRepo</tt> to the test case.
	 */
	protected void setUp() throws Exception {
		super.setUp();
		externalRepo = createTempDir("hudson-gittest");
		exec(externalRepo, "git", "init");
		exec(externalRepo, "git", "config", "--add", "user.name", "John Doe");
		exec(externalRepo, "git", "config", "--add", "user.email", "john@doe.com");
		createTestFile();
		exec(externalRepo, "git", "add", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 1");
		exec(externalRepo, "git", "rm", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 2");
	}
	
	protected void createTestFile() throws Exception {
		File testFile = new File(externalRepo, "test");
		FileOutputStream out = new FileOutputStream(testFile);
		IOUtil.copy("Hello, World!", out);
		out.close();
	}

	public void testGitSCM() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), null, false, false, null, new ArrayList<RemoteRepository>(), null, null));
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertSuccess(r);
        ChangeLogSet changes = b.getChangeSet();
        // First-time build does not create change sets
        assertEquals(0, changes.getItems().length);

		createTestFile();
		exec(externalRepo, "git", "add", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 3");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        changes = b.getChangeSet();
        assertEquals(1, changes.getItems().length);
        ChangeLogSet.Entry lastChange = (ChangeLogSet.Entry)changes.getItems()[0];
        assertEquals("Commit 3", lastChange.getMsg().trim());
        assertEquals("John Doe", lastChange.getAuthor().getId().trim());
	}
}
