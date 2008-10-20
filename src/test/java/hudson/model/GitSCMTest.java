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
		createTestFile("test");
		exec(externalRepo, "git", "add", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 1");
		exec(externalRepo, "git", "rm", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 2");
	}
	
	protected void createTestFile(String name) throws Exception {
		File testFile = new File(externalRepo, name);
		FileOutputStream out = new FileOutputStream(testFile);
		IOUtil.copy("Hello, World!", out);
		out.close();
	}

	/**
	 * Test GitSCM with no specific branch, ie GitSCM will build all "tip" branches
	 * @throws Exception
	 */
	public void testGitSCMAllBranches() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), null, false, false, null, new ArrayList<RemoteRepository>(), null, null));
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertSuccess(r);
        ChangeLogSet changes = b.getChangeSet();
        // First-time build does not create change sets
        assertEquals(0, changes.getItems().length);

		createTestFile("test");
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

		exec(externalRepo, "git", "branch", "newbranch");
		createTestFile("test2");
		exec(externalRepo, "git", "add", "test2");
		exec(externalRepo, "git", "commit", "-m", "Commit in newbranch");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        changes = b.getChangeSet();
        assertEquals(1, changes.getItems().length);
        lastChange = (ChangeLogSet.Entry)changes.getItems()[0];
        assertEquals("Commit in newbranch", lastChange.getMsg().trim());
	}

	/**
	 * Test GitSCM with a specific branch
	 * @throws Exception
	 */
	public void testGitSCMValidBranch() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), "origin/newbranch", false, false, null, new ArrayList<RemoteRepository>(), null, null));
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertFailure(r);

		exec(externalRepo, "git", "checkout", "-b", "newbranch");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        ChangeLogSet changes = b.getChangeSet();
        // First-time build does not create change sets
        assertEquals(0, changes.getItems().length);

		createTestFile("test2");
		exec(externalRepo, "git", "add", "test2");
		exec(externalRepo, "git", "commit", "-m", "Commit in newbranch");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        changes = b.getChangeSet();
        // We built newbranch
        assertEquals(1, changes.getItems().length);
        ChangeLogSet.Entry lastChange = (ChangeLogSet.Entry)changes.getItems()[0];
        assertEquals("Commit in newbranch", lastChange.getMsg().trim());

		exec(externalRepo, "git", "checkout", "master");
		createTestFile("test");
		exec(externalRepo, "git", "add", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 3");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        changes = b.getChangeSet();
        // We didn't build master
        assertEquals(0, changes.getItems().length);
	}

	/**
	 * Test GitSCM with a specific branch that cannot be resolved as a valid branch name
	 * @throws Exception
	 */
	public void testGitSCMBadBranch() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), "newbranch", false, false, null, new ArrayList<RemoteRepository>(), null, null));
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertFailure(r);

		exec(externalRepo, "git", "checkout", "-b", "newbranch");
        b = build(p);
        r = b.getResult();
        // Git doesn't know about newbranch, only about origin/newbranch
        assertFailure(r);
	}
}
